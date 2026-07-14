package com.hackathon.dto;

public record DashboardSummary(
        long events,
        long participants,
        long checkedIn,
        long assigned,
        long feedbackSubmitted,
        long emailsSent
) {
}
