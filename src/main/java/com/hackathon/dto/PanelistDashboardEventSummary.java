package com.hackathon.dto;

import com.hackathon.entity.FeedbackType;
import java.time.LocalDateTime;
import java.util.List;

public record PanelistDashboardEventSummary(
        Long eventId,
        String eventName,
        long participantCount,
        long feedbackCount,
        List<FeedbackType> feedbackTypes,
        LocalDateTime lastFeedbackAt
) {
}