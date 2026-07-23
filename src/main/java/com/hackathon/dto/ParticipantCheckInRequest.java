package com.hackathon.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ParticipantCheckInRequest(
        @NotBlank @Email String email,
        @NotBlank String participantCode
) {
}
