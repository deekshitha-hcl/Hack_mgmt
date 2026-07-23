package com.hackathon.entity;

/**
 * Enum tracking the status of AI resume analysis for a participant.
 */
public enum AiAnalysisStatus {
    NOT_STARTED("Analysis not yet initiated"),
    PENDING("Queued for analysis, waiting for batch processing"),
    PROCESSING("Currently being processed by AI service"),
    SUCCESS("Analysis completed successfully"),
    FAILED("Analysis failed after all retry attempts"),
    SKIPPED("Analysis skipped (no resume provided)");

    private final String description;

    AiAnalysisStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
