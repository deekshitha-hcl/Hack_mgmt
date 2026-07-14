package com.hackathon.dto;

import com.hackathon.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotBlank
        String username,
        @Email
        @NotBlank
        String email,
        @NotBlank
        String password,
        @NotNull
        Role role
) {
}
