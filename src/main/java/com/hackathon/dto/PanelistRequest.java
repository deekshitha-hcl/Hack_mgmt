package com.hackathon.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PanelistRequest(
                @NotBlank String name,
                @NotBlank @Email String email,
                @NotBlank String domain,
                @NotBlank String password) {
}
