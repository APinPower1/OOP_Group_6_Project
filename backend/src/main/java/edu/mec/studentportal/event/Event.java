package edu.mec.studentportal.event;

import java.time.LocalDateTime;

/** Immutable event domain object; no JDBC or framework dependencies. */
public record Event(
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
    LocalDateTime updatedAt
) {}
