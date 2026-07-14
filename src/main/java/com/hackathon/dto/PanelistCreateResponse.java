package com.hackathon.dto;

public record PanelistCreateResponse(
        Long id,
        String name,
        String email,
        String domain,
        String temporaryPassword) {
}
