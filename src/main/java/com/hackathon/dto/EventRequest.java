package com.hackathon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Request body for creating a new event.
 * Status is auto-computed from startDate / endDate and must not be supplied.
 */
public record EventRequest(
        @NotBlank String name,
        String description,
        @NotNull LocalDate startDate,
        LocalDate endDate
) {
}
