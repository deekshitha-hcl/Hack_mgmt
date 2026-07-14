package com.hackathon.dto;

import com.hackathon.entity.PanelistAvailability;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PanelistRegistrationRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String domain,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotNull PanelistAvailability availability,
        String customAvailabilityTime) {
}
