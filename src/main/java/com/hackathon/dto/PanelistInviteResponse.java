package com.hackathon.dto;

import java.time.LocalDateTime;

public record PanelistInviteResponse(
        String token,
        String registrationUrl,
        LocalDateTime expiresAt) {
}
