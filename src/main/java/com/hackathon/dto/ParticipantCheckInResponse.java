package com.hackathon.dto;

public record ParticipantCheckInResponse(
        Long participantId,
        String participantCode,
        String name,
        String email,
        String status,
        String message,
        String dashboardUrl,
        String supportUrl,
        boolean resumeAnalysisTriggered
) {
}
