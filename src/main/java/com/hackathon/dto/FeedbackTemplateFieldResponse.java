package com.hackathon.dto;

public record FeedbackTemplateFieldResponse(
        String name,
        String type,
        boolean required
) {
}
