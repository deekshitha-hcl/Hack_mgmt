package com.hackathon.entity;

public enum EventStatus {
    /** Event date is in the future; registrations not yet open. */
    UPCOMING,
    /** Event is currently active; registrations are open. */
    OPEN,
    /** Event end date has passed; registrations are closed. */
    CLOSED,
    /** Event was cancelled manually and should remain in this terminal state. */
    CANCELLED
}
