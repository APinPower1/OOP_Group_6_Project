package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Event representation per API_CONTRACT.md; counts are server-calculated. */
public record EventDto(
    long id,
    String title,
    String description,
    String venue,
    LocalDateTime startTime,
    LocalDateTime endTime,
    int capacity,
    EventStatus status,
    long createdBy,
    Long reviewedBy,
    String reviewReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Integer registeredCount,
    Integer availableSlots
) {}
