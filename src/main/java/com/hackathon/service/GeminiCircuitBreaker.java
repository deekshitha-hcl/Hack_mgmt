package com.hackathon.service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Circuit breaker for the Gemini API.
 * If 3+ consecutive Gemini failures occur, the circuit opens and pauses all
 * Gemini calls for a configurable duration to avoid hammering a down API.
 */
@Component
public class GeminiCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(GeminiCircuitBreaker.class);

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile LocalDateTime circuitOpenUntil = null;

    @Value("${app.ai.circuit-breaker-threshold:3}")
    private int failureThreshold;

    @Value("${app.ai.circuit-breaker-pause-seconds:300}")
    private long pauseSeconds;

    /**
     * Returns true if the circuit is open (Gemini calls should be blocked).
     * Auto-resets once the pause window has expired.
     */
    public boolean isOpen() {
        if (circuitOpenUntil != null) {
            if (LocalDateTime.now().isBefore(circuitOpenUntil)) {
                return true;
            }
            // Pause expired — reset
            consecutiveFailures.set(0);
            circuitOpenUntil = null;
            log.info("[CIRCUIT-BREAKER] ✓ CLOSED — pause expired, resuming Gemini calls");
        }
        return false;
    }

    /** Call after a successful Gemini API response. */
    public void recordSuccess() {
        int prev = consecutiveFailures.getAndSet(0);
        if (prev > 0) {
            log.info("[CIRCUIT-BREAKER] ✓ RESET — Gemini succeeded, cleared {} consecutive failures", prev);
        }
        circuitOpenUntil = null;
    }

    /** Call whenever a Gemini API call fails (network error, bad response, etc.). */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("[CIRCUIT-BREAKER] ⚠ Gemini failure #{} (threshold={})", failures, failureThreshold);
        if (failures >= failureThreshold && circuitOpenUntil == null) {
            circuitOpenUntil = LocalDateTime.now().plusSeconds(pauseSeconds);
            log.error("[CIRCUIT-BREAKER] 🔴 OPEN — {} consecutive Gemini failures. Pausing until {}",
                    failures, circuitOpenUntil);
        }
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public LocalDateTime getCircuitOpenUntil() {
        return circuitOpenUntil;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public long getPauseSeconds() {
        return pauseSeconds;
    }
}
