package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Club representation per API_CONTRACT.md. */
public record ClubDto(
    long id,
    String name,
    String description,
    String category,
    boolean active,
    int activeMemberCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
