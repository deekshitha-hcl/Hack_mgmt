package com.hackathon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AutoSquadRequest(
        @NotNull Long eventId,
        @NotNull @Min(2) Integer minMembers,
        @NotNull @Min(2) Integer maxMembers,
        String squadNamePrefix,
        String techStackFilter
) {
}
