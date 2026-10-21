package edu.mec.studentportal.club;

import java.time.LocalDateTime;

/** Immutable club domain object. */
public record Club(
    long id,
    String name,
    String description,
    String category,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
