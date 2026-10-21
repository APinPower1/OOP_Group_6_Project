package edu.mec.studentportal.event;

import java.time.LocalDateTime;

/**
 * Event response per docs/API_CONTRACT.md. {@code registeredCount} and {@code availableSlots}
 * are always calculated from CONFIRMED registration rows, never from a stored counter.
 */
public record EventResponse(
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
