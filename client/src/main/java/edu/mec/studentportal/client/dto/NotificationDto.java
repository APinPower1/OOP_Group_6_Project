package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Notification representation per API_CONTRACT.md. */
public record NotificationDto(
    long id,
    NotificationType notificationType,
    String title,
    String message,
    Long announcementId,
    Long eventId,
    LocalDateTime createdAt,
    LocalDateTime readAt
) {}
