package com.hackathon.dto;

import com.hackathon.entity.FeedbackType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record FeedbackRequest(
        @NotNull Long participantId,
        @NotNull Long panelistId,
        @NotNull FeedbackType feedbackType,
        @NotEmpty Map<String, String> fieldValues
) {
}
