package com.hackathon.service;

import com.hackathon.entity.AiAnalysisStatus;
import com.hackathon.entity.Participant;
import com.hackathon.dto.AiBatchMetricsResponse;
import com.hackathon.repository.ParticipantRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Batch processing service for AI resume analysis after participant check-in.
 * Processes multiple participants in batches with automatic retry on failure.
 */
@Service
@Slf4j
public class ParticipantAiBatchProcessingService {

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private ParticipantAiAnalysisService aiAnalysisService;

    @Autowired
    private GeminiCircuitBreaker circuitBreaker;

    @Value("${app.ai.batch-size:5}")
    private int batchSize;

    @Value("${app.ai.batch-processing-enabled:true}")
    private boolean batchProcessingEnabled;

    @Value("${app.ai.max-retries:3}")
    private int maxRetries;

    @Value("${app.ai.retry-backoff-multiplier:2}")
    private double retryBackoffMultiplier;

    @Value("${app.ai.retry-delay-seconds:60}")
    private long retryDelaySeconds;

    @Value("${app.ai.batch-interval-ms:30000}")
    private int batchIntervalMs;

    /**
     * Scheduled task that runs periodically to process pending AI analysis requests.
     * Runs every 30 seconds by default (configurable via app.ai.batch-interval-ms).
     * 
     * Implements exponential backoff retry logic:
     * - First attempt: immediate
     * - Failed attempts are retried with exponential backoff
     * - Max retries: 3 (configurable via app.ai.max-retries)
     * - Backoff formula: delay = base_delay * (multiplier ^ attempt_number)
     */
    @Scheduled(fixedRateString = "${app.ai.batch-interval-ms:30000}")
    public void processPendingAnalysisBatch() {
        if (!batchProcessingEnabled) {
            log.debug("[AI-BATCH] Batch processing is disabled");
            return;
        }
        if (circuitBreaker.isOpen()) {
            log.warn("[AI-BATCH] 🔴 Circuit breaker OPEN — skipping batch. Gemini resumes at {}",
                    circuitBreaker.getCircuitOpenUntil());
            return;
        }

        try {
            // Fetch batch of pending participants (only those within retry limit)
            List<Participant> pendingParticipants = participantRepository.findPendingAiAnalysisWithRetry(maxRetries, batchSize);

            if (pendingParticipants.isEmpty()) {
                log.debug("[AI-BATCH] No pending AI analysis requests in queue");
                return;
            }

            log.info("[AI-BATCH] ═══════════════════════════════════════════════════");
            log.info("[AI-BATCH] Batch run started");
            log.info("[AI-BATCH]   Pending in queue : {}", pendingParticipants.size());
            log.info("[AI-BATCH]   Batch size       : {}", batchSize);
            log.info("[AI-BATCH]   Max retries      : {}", maxRetries);
            log.info("[AI-BATCH] ═══════════════════════════════════════════════════");

            int queued = 0;
            int waitingBackoff = 0;
            int failureCount = 0;

            for (Participant participant : pendingParticipants) {
                try {
                    int attemptNum = participant.getAiAnalysisAttemptCount() == null
                            ? 0
                            : participant.getAiAnalysisAttemptCount();

                    // Check if this participant is ready for retry (based on exponential backoff)
                    if (attemptNum > 0 && !isReadyForRetry(participant)) {
                        long backoff = calculateBackoffDelay(attemptNum);
                        log.info("[AI-BATCH] ⏱ Waiting backoff — participantId={} ({}) attempt={} backoff={}s",
                                participant.getId(), participant.getName(), attemptNum, backoff);
                        waitingBackoff++;
                        continue;
                    }

                    log.info("[AI-BATCH] → Queuing — participantId={} ({}) attempt={}/{}",
                            participant.getId(), participant.getName(), attemptNum + 1, maxRetries);

                    // Trigger async AI analysis
                    aiAnalysisService.analyzeResumeAfterCheckIn(participant.getId());
                    queued++;

                } catch (Exception e) {
                    failureCount++;
                    log.error("[AI-BATCH] ✗ Failed to queue participantId={} — {}",
                            participant.getId(), e.getMessage());

                    participant.setAiAnalysisAttemptCount((participant.getAiAnalysisAttemptCount() == null ? 0 : participant.getAiAnalysisAttemptCount()) + 1);
                    if (participant.getAiAnalysisAttemptCount() >= maxRetries) {
                        participant.setAiAnalysisPending(false);
                        log.error("[AI-BATCH] ✗ ABANDONED — participantId={} ({}) exceeded max retries ({})",
                                participant.getId(), participant.getName(), maxRetries);
                    }
                    participantRepository.save(participant);
                }
            }

            log.info("[AI-BATCH] ─────────────────────────────────────────────────");
            log.info("[AI-BATCH] Batch run complete");
            log.info("[AI-BATCH]   Queued for analysis   : {}", queued);
            log.info("[AI-BATCH]   Waiting backoff        : {}", waitingBackoff);
            log.info("[AI-BATCH]   Failed to queue        : {}", failureCount);
            log.info("[AI-BATCH] ─────────────────────────────────────────────────");

        } catch (Exception e) {
            log.error("[AI-BATCH] ✗ Critical error during batch processing", e);
        }
    }

    /**
     * Check if a participant with failed attempts is ready for retry based on exponential backoff.
     * Formula: delay = base_delay * (multiplier ^ (attempt_count - 1))
     */
    private boolean isReadyForRetry(Participant participant) {
        if (participant.getLastAiAnalysisAttempt() == null) {
            return true;
        }

        int attemptCount = participant.getAiAnalysisAttemptCount() == null ? 0 : participant.getAiAnalysisAttemptCount();
        long backoffSeconds = calculateBackoffDelay(attemptCount);
        LocalDateTime nextRetryTime = participant.getLastAiAnalysisAttempt().plusSeconds(backoffSeconds);

        boolean isReady = LocalDateTime.now().isAfter(nextRetryTime);
        if (!isReady) {
            long secondsUntilRetry = java.time.temporal.ChronoUnit.SECONDS.between(
                LocalDateTime.now(), nextRetryTime);
            log.debug("[AI-BATCH] Participant ID: {} will retry in {} seconds", participant.getId(), secondsUntilRetry);
        }
        return isReady;
    }

    /**
     * Calculate exponential backoff delay in seconds.
     * Example: attempt=1 → 60s, attempt=2 → 120s, attempt=3 → 240s (with multiplier=2)
     */
    private long calculateBackoffDelay(int attemptCount) {
        if (attemptCount <= 0) {
            return retryDelaySeconds;
        }
        double delaySeconds = retryDelaySeconds * Math.pow(retryBackoffMultiplier, attemptCount - 1);
        return Math.min((long) delaySeconds, Long.MAX_VALUE);
    }

    /**
     * Manually trigger batch processing (useful for testing/admin actions).
     *
     * @return Number of participants processed
     */
    public int triggerBatchProcessing() {
        List<Participant> pendingParticipants = participantRepository.findPendingAiAnalysisWithRetry(maxRetries, batchSize);
        
        int count = 0;
        for (Participant participant : pendingParticipants) {
            try {
                aiAnalysisService.analyzeResumeAfterCheckIn(participant.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to process AI analysis for participant ID: {}", 
                    participant.getId(), e);
                int attempts = participant.getAiAnalysisAttemptCount() == null
                        ? 0
                        : participant.getAiAnalysisAttemptCount();
                participant.setAiAnalysisAttemptCount(attempts + 1);
                if (participant.getAiAnalysisAttemptCount() >= maxRetries) {
                    participant.setAiAnalysisPending(false);
                }
                participantRepository.save(participant);
            }
        }

        return count;
    }

    /**
     * Returns live metrics about the AI analysis queue and circuit breaker state.
     */
    public AiBatchMetricsResponse getMetrics() {
        long pending   = participantRepository.countByAiAnalysisPendingTrue();
        long processing = participantRepository.countByAiAnalysisStatus(AiAnalysisStatus.PROCESSING);
        long success   = participantRepository.countByAiAnalysisStatus(AiAnalysisStatus.SUCCESS);
        long failed    = participantRepository.countByAiAnalysisStatus(AiAnalysisStatus.FAILED);
        long skipped   = participantRepository.countByAiAnalysisStatus(AiAnalysisStatus.SKIPPED);
        long abandoned = participantRepository.countAbandoned(maxRetries);
        return new AiBatchMetricsResponse(
                pending, processing, success, failed, skipped, abandoned,
                circuitBreaker.getConsecutiveFailures(),
                circuitBreaker.isOpen(),
                circuitBreaker.getCircuitOpenUntil(),
                batchProcessingEnabled,
                maxRetries, batchSize, batchIntervalMs
        );
    }

    /**
     * Get current count of pending AI analysis requests.
     *
     * @return Number of pending participants
     */
    public long getPendingAnalysisCount() {
        List<Participant> pending = participantRepository.findPendingAiAnalysisWithRetry(maxRetries, Integer.MAX_VALUE);
        return pending.size();
    }

    /**
     * Get count of participants that have failed and are not being retried.
     *
     * @return Number of abandoned participants
     */
    public long getAbandonedAnalysisCount() {
        return participantRepository.countAbandoned(maxRetries);
    }
}
