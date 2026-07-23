package com.hackathon.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * Response DTO for checking AI resume analysis status.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AIAnalysisStatusResponse(
    Long participantId,
    String participantName,
    String status,            // NOT_STARTED, PENDING, PROCESSING, SUCCESS, FAILED, SKIPPED
    String statusDescription,
    Integer attemptCount,
    Integer maxRetries,
    LocalDateTime lastAttemptTime,
    String lastErrorMessage,
    Integer aiScore,
    String skills,
    String message
) {
}
