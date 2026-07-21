package com.hackathon.dto;

import java.util.List;

public record FeedbackTemplateResponse(
        String feedbackType,
        List<FeedbackTemplateFieldResponse> fields
) {
}
