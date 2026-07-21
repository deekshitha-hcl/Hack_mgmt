package com.hackathon.dto;

import java.util.List;

public record PanelistDashboardResponse(
        Long panelistId,
        String panelistName,
        String panelistEmail,
        long eventsHandled,
        long participantsFeedbackGiven,
        long feedbackSubmitted,
        List<PanelistDashboardEventSummary> events
) {
}