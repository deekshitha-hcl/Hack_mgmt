package com.hackathon.service;

import com.hackathon.dto.ResumeAnalysis;
import com.hackathon.entity.Participant;
import com.hackathon.entity.AiAnalysisStatus;
import com.hackathon.repository.ParticipantRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ParticipantAiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantAiAnalysisService.class);

    private final ParticipantRepository participantRepository;
    private final FileStorageService fileStorageService;
    private final ResumeAnalysisService resumeAnalysisService;

    public ParticipantAiAnalysisService(ParticipantRepository participantRepository,
                                        FileStorageService fileStorageService,
                                        ResumeAnalysisService resumeAnalysisService) {
        this.participantRepository = participantRepository;
        this.fileStorageService = fileStorageService;
        this.resumeAnalysisService = resumeAnalysisService;
    }

    @Async
    public void analyzeResumeAfterCheckIn(Long participantId) {
        log.info("[AI-PARSE] ► Triggered for participantId={}", participantId);
        participantRepository.findById(participantId).ifPresentOrElse(this::analyzeAndSave, () ->
                log.warn("[AI-PARSE] ✗ Participant not found in DB participantId={}", participantId));
    }

    private void analyzeAndSave(Participant participant) {
        long startTime = System.currentTimeMillis();
        String resumeUrl = participant.getResumeUrl();

        log.info("[AI-PARSE] ─────────────────────────────────────────────────");
        log.info("[AI-PARSE] Starting resume analysis");
        log.info("[AI-PARSE]   Participant ID   : {}", participant.getId());
        log.info("[AI-PARSE]   Name             : {}", participant.getName());
        log.info("[AI-PARSE]   Email            : {}", participant.getEmail());
        log.info("[AI-PARSE]   Attempt #        : {}", (participant.getAiAnalysisAttemptCount() == null ? 0 : participant.getAiAnalysisAttemptCount()) + 1);
        log.info("[AI-PARSE]   Resume URL       : {}", resumeUrl);
        log.info("[AI-PARSE] ─────────────────────────────────────────────────");

        if (resumeUrl == null || resumeUrl.isBlank()) {
            log.warn("[AI-PARSE] ✗ SKIPPED — No resume uploaded for participantId={} ({})",
                    participant.getId(), participant.getName());
            participant.setAiAnalysisPending(false);
            participant.setAiAnalysisStatus(AiAnalysisStatus.SKIPPED);
            participant.setLastAiAnalysisAttempt(LocalDateTime.now());
            participantRepository.save(participant);
            return;
        }

        Path resumePath = resolveResumePath(resumeUrl);
        log.info("[AI-PARSE] Resolved resume path: {}", resumePath);

        if (!Files.exists(resumePath)) {
            log.warn("[AI-PARSE] ✗ FAILED — Resume file not found on disk");
            log.warn("[AI-PARSE]   Expected path: {}", resumePath);
            participant.setAiAnalysisPending(false);
            participant.setAiAnalysisStatus(AiAnalysisStatus.FAILED);
            participant.setLastAiAnalysisError("Resume file not found: " + resumePath);
            participant.setLastAiAnalysisAttempt(LocalDateTime.now());
            participant.setAiAnalysisAttemptCount((participant.getAiAnalysisAttemptCount() == null ? 0 : participant.getAiAnalysisAttemptCount()) + 1);
            participantRepository.save(participant);
            return;
        }

        try {
            // Mark as PROCESSING — lock this participant so the batch won't re-dispatch it
            int currentAttempts = (participant.getAiAnalysisAttemptCount() == null ? 0 : participant.getAiAnalysisAttemptCount());
            participant.setAiAnalysisStatus(AiAnalysisStatus.PROCESSING);
            participant.setAiAnalysisPending(false);
            participant.setAiAnalysisAttemptCount(currentAttempts + 1);
            participant.setLastAiAnalysisAttempt(LocalDateTime.now());
            participantRepository.save(participant);

            byte[] bytes = Files.readAllBytes(resumePath);
            log.info("[AI-PARSE] Resume file loaded: {} bytes ({} KB)",
                    bytes.length, String.format("%.1f", bytes.length / 1024.0));
            log.info("[AI-PARSE] Sending to Gemini API for analysis...");

            MultipartFile resumeFile = new InMemoryMultipartFile(
                    resumePath.getFileName().toString(),
                    "application/octet-stream",
                    bytes);

            ResumeAnalysis analysis = resumeAnalysisService.analyze(resumeFile, participant.getExperienceYears());

            long elapsed = System.currentTimeMillis() - startTime;
            participant.setAiScore(analysis.aiScore());
            participant.setSkills(analysis.skills());
            participant.setResumeAnalysisJson(analysis.structuredJson());
            participant.setAiAnalysisPending(false);
            participant.setAiAnalysisStatus(AiAnalysisStatus.SUCCESS);
            participant.setLastAiAnalysisError(null);
            participantRepository.save(participant);

            log.info("[AI-PARSE] ✓ SUCCESS — Resume analysis complete in {}ms", elapsed);
            log.info("[AI-PARSE]   Participant : {} (ID: {})", participant.getName(), participant.getId());
            log.info("[AI-PARSE]   AI Score    : {}/100", analysis.aiScore());
            log.info("[AI-PARSE]   Skills      : {}", analysis.skills());
            log.info("[AI-PARSE] ─────────────────────────────────────────────────");

        } catch (IOException | RuntimeException ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            int attempts = participant.getAiAnalysisAttemptCount() == null ? 1 : participant.getAiAnalysisAttemptCount();
            log.error("[AI-PARSE] ✗ FAILED after {}ms — participantId={} ({})",
                    elapsed, participant.getId(), participant.getName());
            log.error("[AI-PARSE]   Error     : {}", ex.getMessage());
            log.error("[AI-PARSE]   Attempt # : {}/{} — will retry if below max",
                    attempts, "max-retries");
            log.debug("[AI-PARSE] Full exception:", ex);

            participant.setAiAnalysisStatus(AiAnalysisStatus.FAILED);
            participant.setLastAiAnalysisError(ex.getMessage());
            participant.setLastAiAnalysisAttempt(LocalDateTime.now());
            // Re-enable pending so the batch can retry this participant
            participant.setAiAnalysisPending(true);
            participantRepository.save(participant);

            log.info("[AI-PARSE] ─────────────────────────────────────────────────");
        }
    }

    private Path resolveResumePath(String resumeUrl) {
        String relative = resumeUrl.startsWith("/uploads/")
                ? resumeUrl.substring("/uploads/".length())
                : resumeUrl;
        return fileStorageService.getUploadRoot().resolve(relative).normalize();
    }

    private static final class InMemoryMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return "resume";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
