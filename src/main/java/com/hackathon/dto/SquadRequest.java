package com.hackathon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SquadRequest(
        @NotNull Long eventId,
        @NotBlank String name
) {
}
