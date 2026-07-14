package com.hackathon.service;

import com.hackathon.dto.ParticipantRegistrationRequest;
import com.hackathon.dto.ResumeAnalysis;
import com.hackathon.entity.Participant;
import com.hackathon.entity.ParticipantStatus;
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
    private final ResumeAnalysisService resumeAnalysisService;
    private final EmailService emailService;

    public ParticipantService(ParticipantRepository participantRepository, EventRepository eventRepository,
                              FeedbackRepository feedbackRepository, SquadMemberRepository squadMemberRepository,
                              FileStorageService fileStorageService, ResumeAnalysisService resumeAnalysisService,
                              EmailService emailService) {
        this.participantRepository = participantRepository;
        this.eventRepository = eventRepository;
        this.feedbackRepository = feedbackRepository;
        this.squadMemberRepository = squadMemberRepository;
        this.fileStorageService = fileStorageService;
        this.resumeAnalysisService = resumeAnalysisService;
        this.emailService = emailService;
    }

    @Transactional
    public Participant register(ParticipantRegistrationRequest request, MultipartFile resume, MultipartFile photo) {
        log.info("Register phase=start email={} eventId={}", request.email(), request.eventId());

        eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.eventId()));

        participantRepository.findByEmail(request.email()).ifPresent(p -> {
            throw new BadRequestException("Participant already registered with email: " + request.email());
        });

        // Run Gemini analysis and both file writes concurrently — they are independent I/O operations
        log.info("Register phase=parallel_tasks_start email={}", request.email());
        CompletableFuture<ResumeAnalysis> analysisFuture = CompletableFuture.supplyAsync(
                () -> resumeAnalysisService.analyze(resume, request.experienceYears()));
        CompletableFuture<String> resumeUrlFuture = CompletableFuture.supplyAsync(
                () -> fileStorageService.store(resume, "resumes"));
        CompletableFuture<String> photoUrlFuture = CompletableFuture.supplyAsync(
                () -> fileStorageService.store(photo, "photos"));

        try {
            CompletableFuture.allOf(analysisFuture, resumeUrlFuture, photoUrlFuture).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Registration step failed", cause);
        }

        ResumeAnalysis analysis = analysisFuture.join();
        String resumeUrl = resumeUrlFuture.join();
        String photoUrl = photoUrlFuture.join();
        log.info("Register phase=parallel_tasks_done email={} aiScore={}", request.email(), analysis.aiScore());

        Participant participant = Participant.builder()
                .eventId(request.eventId())
                .name(request.name())
                .email(request.email())
                .techStack(request.techStack())
                .phone(request.phone())
                .experienceYears(request.experienceYears())
                .resumeUrl(resumeUrl)
                .photoUrl(photoUrl)
                .skills(analysis.skills())
                .aiScore(analysis.aiScore())
                .resumeAnalysisJson(analysis.structuredJson())
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

}
