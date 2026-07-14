package com.hackathon.dto;

public record AuthenticationResponse(
        String token,
        String role,
        String username
) {
}
