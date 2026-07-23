package com.hackathon.service;

import com.hackathon.dto.ParticipantRegistrationRequest;
import com.hackathon.dto.ParticipantCheckInNavigationResponse;
import com.hackathon.dto.ParticipantCheckInRequest;
import com.hackathon.dto.ParticipantCheckInResponse;
import com.hackathon.dto.AIAnalysisStatusResponse;
import com.hackathon.entity.Event;
import com.hackathon.entity.Participant;
import com.hackathon.entity.ParticipantStatus;
import com.hackathon.entity.AiAnalysisStatus;
import com.hackathon.exception.BadRequestException;
import com.hackathon.exception.ResourceNotFoundException;
import com.hackathon.repository.EventRepository;
import com.hackathon.repository.FeedbackRepository;
import com.hackathon.repository.ParticipantRepository;
import com.hackathon.repository.SquadMemberRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ParticipantService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantService.class);

    private final ParticipantRepository participantRepository;
    private final EventRepository eventRepository;
    private final FeedbackRepository feedbackRepository;
    private final SquadMemberRepository squadMemberRepository;
    private final FileStorageService fileStorageService;
    private final ParticipantAiAnalysisService participantAiAnalysisService;
    private final EmailService emailService;
    private final String frontendUrl;

    public ParticipantService(ParticipantRepository participantRepository, EventRepository eventRepository,
                              FeedbackRepository feedbackRepository, SquadMemberRepository squadMemberRepository,
                              FileStorageService fileStorageService,
                              ParticipantAiAnalysisService participantAiAnalysisService,
                              EmailService emailService,
                              @org.springframework.beans.factory.annotation.Value("${app.frontend-url:}")
                              String frontendUrl) {
        this.participantRepository = participantRepository;
        this.eventRepository = eventRepository;
        this.feedbackRepository = feedbackRepository;
        this.squadMemberRepository = squadMemberRepository;
        this.fileStorageService = fileStorageService;
        this.participantAiAnalysisService = participantAiAnalysisService;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl;
    }

    @Transactional
    public Participant register(ParticipantRegistrationRequest request, MultipartFile resume, MultipartFile photo) {
        log.info("Register phase=start email={} eventId={}", request.email(), request.eventId());

        eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.eventId()));

        participantRepository.findByEmail(request.email()).ifPresent(p -> {
            throw new BadRequestException("Participant already registered with email: " + request.email());
        });

        // Pre-registration stores files only. AI analysis runs after venue check-in.
        log.info("Register phase=parallel_tasks_start email={}", request.email());
        CompletableFuture<String> resumeUrlFuture = CompletableFuture.supplyAsync(
                () -> fileStorageService.store(resume, "resumes"));
        CompletableFuture<String> photoUrlFuture = CompletableFuture.supplyAsync(
                () -> fileStorageService.store(photo, "photos"));

        try {
            CompletableFuture.allOf(resumeUrlFuture, photoUrlFuture).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Registration step failed", cause);
        }

        String resumeUrl = resumeUrlFuture.join();
        String photoUrl = photoUrlFuture.join();
        log.info("Register phase=parallel_tasks_done email={} resumeStored={} photoStored={}",
            request.email(), resumeUrl != null, photoUrl != null);

        Participant participant = Participant.builder()
                .eventId(request.eventId())
                .name(request.name())
                .email(request.email())
                .techStack(request.techStack())
                .phone(request.phone())
                .experienceYears(request.experienceYears())
                .resumeUrl(resumeUrl)
                .photoUrl(photoUrl)
            .skills(null)
            .aiScore(null)
            .resumeAnalysisJson(null)
                .status(ParticipantStatus.REGISTERED)
                .build();

        participant = participantRepository.save(participant);
        // Derive code from the DB-assigned id — race-condition-free, no extra query needed
        participant.setParticipantCode("PART-%04d".formatted(participant.getId()));
        log.info("Register phase=saved participantId={} participantCode={}",
                participant.getId(), participant.getParticipantCode());

        // Fires asynchronously; response returns immediately after DB save
        emailService.sendRegistrationConfirmation(participant.getId(), participant.getEmail(), participant.getName(),
                participant.getParticipantCode());
        log.info("Register phase=done participantId={} email={}", participant.getId(), participant.getEmail());
        return participant;
    }

    public ParticipantCheckInNavigationResponse getCheckInNavigation(Long eventId, String email) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        String baseMessage = "Welcome. Verify your registration using email and participant code.";
        String regUrl = event.getRegistrationUrl();
        String verifyEndpoint = "/api/participants/check-in/verify";

        if (email == null || email.isBlank()) {
            return new ParticipantCheckInNavigationResponse(
                    event.getId(), event.getName(), baseMessage,
                    regUrl, verifyEndpoint, "POST", "email,participantCode",
                    null, null, null, null, null);
        }

        // Email provided — look up participant for this event
        return participantRepository.findByEmail(email)
                .filter(p -> eventId.equals(p.getEventId()))
                .map(p -> {
                    boolean alreadyCheckedIn = p.getStatus() == ParticipantStatus.CHECKED_IN;
                    String lookupMsg = alreadyCheckedIn
                            ? "You are already checked in."
                            : "Registration confirmed. Enter your participant code to complete check-in.";
                    return new ParticipantCheckInNavigationResponse(
                            event.getId(), event.getName(), baseMessage,
                            regUrl, verifyEndpoint, "POST", "email,participantCode",
                            true, p.getId(), p.getName(), p.getStatus().name(), lookupMsg);
                })
                .orElseGet(() -> new ParticipantCheckInNavigationResponse(
                        event.getId(), event.getName(), baseMessage,
                        regUrl, verifyEndpoint, "POST", "email,participantCode",
                        false, null, null, null,
                        "No registration found for this email. Please register first."));
    }

    @Transactional
    public ParticipantCheckInResponse verifyAndCheckIn(ParticipantCheckInRequest request) {
        Participant participant = participantRepository
                .findByEmailAndParticipantCode(request.email(), request.participantCode())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found for provided email/code."));

        if (participant.getStatus() == ParticipantStatus.CHECKED_IN) {
            throw new BadRequestException("Participant already checked in.");
        }

        participant.setStatus(ParticipantStatus.CHECKED_IN);
        participant.setAiAnalysisPending(true);
        participant.setAiAnalysisStatus(AiAnalysisStatus.PENDING);
        if (participant.getAiAnalysisAttemptCount() == null) {
            participant.setAiAnalysisAttemptCount(0);
        }
        participantRepository.save(participant);
        log.info("[CHECK-IN] Participant checked in and queued for AI analysis: id={} name={} email={}",
                participant.getId(), participant.getName(), participant.getEmail());

        return new ParticipantCheckInResponse(
                participant.getId(),
                participant.getParticipantCode(),
                participant.getName(),
                participant.getEmail(),
                participant.getStatus().name(),
                "Check-in successful. Resume analysis has been queued for batch processing.",
                buildFrontendPath("/participant/dashboard"),
                buildFrontendPath("/support"),
                true
        );
    }

    private String buildFrontendPath(String path) {
        if (frontendUrl.isBlank()) {
            return path;
        }
        if (frontendUrl.endsWith("/")) {
            return frontendUrl.substring(0, frontendUrl.length() - 1) + path;
        }
        return frontendUrl + path;
    }

    public List<Participant> findAll() {
        return participantRepository.findAll();
    }

    public List<Participant> findByEvent(Long eventId) {
        return participantRepository.findByEventId(eventId);
    }

    public Participant findById(Long id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + id));
    }

    @Transactional
    public void delete(Long id) {
        Participant participant = findById(id);
        feedbackRepository.deleteByParticipantId(id);
        squadMemberRepository.deleteByParticipantId(id);
        participantRepository.delete(participant);
    }

    public AIAnalysisStatusResponse getAiAnalysisStatus(Long participantId, int maxRetries) {
        Participant participant = findById(participantId);

        AiAnalysisStatus status = participant.getAiAnalysisStatus() != null
                ? participant.getAiAnalysisStatus()
                : AiAnalysisStatus.NOT_STARTED;
        String statusDescription = status.getDescription();
        String message = buildStatusMessage(participant, maxRetries);

        return AIAnalysisStatusResponse.builder()
                .participantId(participant.getId())
                .participantName(participant.getName())
                .status(status.name())
                .statusDescription(statusDescription)
                .attemptCount(participant.getAiAnalysisAttemptCount() == null ? 0 : participant.getAiAnalysisAttemptCount())
                .maxRetries(maxRetries)
                .lastAttemptTime(participant.getLastAiAnalysisAttempt())
                .lastErrorMessage(participant.getLastAiAnalysisError())
                .aiScore(participant.getAiScore())
                .skills(participant.getSkills())
                .message(message)
                .build();
    }

    private String buildStatusMessage(Participant participant, int maxRetries) {
        AiAnalysisStatus status = participant.getAiAnalysisStatus() != null
                ? participant.getAiAnalysisStatus()
                : AiAnalysisStatus.NOT_STARTED;
        int attempts = participant.getAiAnalysisAttemptCount() == null ? 0 : participant.getAiAnalysisAttemptCount();
        return switch (status) {
            case NOT_STARTED -> "Analysis not yet started.";
            case PENDING -> "Your resume is queued for AI analysis. It will be processed shortly.";
            case PROCESSING -> "Your resume is currently being analyzed. Please check back soon.";
            case SUCCESS -> "Resume analysis completed successfully.";
            case SKIPPED -> "Analysis was skipped (no resume provided).";
            case FAILED -> {
                if (attempts >= maxRetries) {
                    yield "Analysis failed after " + maxRetries + " attempts. Last error: " + participant.getLastAiAnalysisError();
                } else {
                    yield "Previous analysis attempt failed. Will retry automatically.";
                }
            }
        };
    }

}
