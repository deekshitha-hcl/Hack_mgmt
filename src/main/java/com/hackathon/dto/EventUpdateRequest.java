package com.hackathon.dto;

import java.time.LocalDate;

/**
 * Request body for updating an existing event.
 * All fields are optional; only non-null values are applied.
 * Providing new dates automatically recalculates the event status.
 */
public record EventUpdateRequest(
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        Boolean cancelled
) {
}
