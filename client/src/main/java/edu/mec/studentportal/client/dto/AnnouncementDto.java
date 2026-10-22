package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Announcement representation per API_CONTRACT.md. */
public record AnnouncementDto(
    long id,
    String title,
    String content,
    long createdBy,
    String audienceRole,
    AnnouncementStatus status,
    LocalDateTime createdAt,
    LocalDateTime publishedAt,
    LocalDateTime archivedAt
) {}
