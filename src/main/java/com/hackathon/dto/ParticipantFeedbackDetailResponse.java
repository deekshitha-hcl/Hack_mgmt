package com.hackathon.dto;

import com.hackathon.entity.FeedbackType;
import java.time.LocalDateTime;
import java.util.Map;

public record ParticipantFeedbackDetailResponse(
        Long id,
        Long participantId,
        Long panelistId,
        String panelistName,
        String panelistEmail,
        FeedbackType feedbackType,
        LocalDateTime submittedAt,
        Map<String, String> fieldValues
) {
}
