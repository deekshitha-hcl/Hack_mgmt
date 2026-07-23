package com.hackathon.dto;

import java.time.LocalDateTime;

public record AiBatchMetricsResponse(
    // Queue counts
    long pendingCount,
    long processingCount,
    long successCount,
    long failedCount,
    long skippedCount,
    long abandonedCount,

    // Circuit breaker state
    int consecutiveGeminiFailures,
    boolean circuitBreakerOpen,
    LocalDateTime circuitBreakerResumesAt,

    // Runtime config
    boolean batchProcessingEnabled,
    int configuredMaxRetries,
    int configuredBatchSize,
    int configuredBatchIntervalMs
) {}
