package com.hackathon.dto;

import com.hackathon.entity.Recommendation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
        @NotNull Long participantId,
        @NotNull Long panelistId,
        @NotNull @Min(1) @Max(5) Integer technicalRating,
        @NotNull @Min(1) @Max(5) Integer communicationRating,
        @NotNull Recommendation recommendation,
        String comments
) {
}
