package com.hackathon.dto;

public record ParticipantCheckInNavigationResponse(
        Long eventId,
        String eventName,
        String message,
        String preRegistrationUrl,
        String verifyEndpoint,
        String method,
        String requiredFields,
        // Populated only when email is supplied in the QR lookup step
        Boolean participantFound,
        Long participantId,
        String participantName,
        String participantStatus,
        String lookupMessage
) {
}
