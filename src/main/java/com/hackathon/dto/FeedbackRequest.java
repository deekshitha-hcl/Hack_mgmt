package com.hackathon.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
        @NotNull Long participantId,
        @NotNull Long panelistId,
        @NotNull @Min(1) @Max(5) Integer technicalRating,
        @NotNull @Min(1) @Max(5) Integer communicationRating,
        @NotNull @Min(1) @Max(5) Integer problemSolvingRating,
        @NotNull @Min(1) @Max(5) Integer attitudeRating,
        @NotNull @Min(1) @Max(5) Integer teamworkRating,
        String comments,
        String strengths,
        String areasOfImprovement
) {
}
