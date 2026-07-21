package com.hackathon.dto;

import java.util.List;

public record ParticipantFeedbackStatusResponse(
        Long participantId,
        int totalSubmitted,
        int totalAllowed,
        boolean completed,
        List<ParticipantFeedbackTypeStatus> feedbackTypes
) {
}
