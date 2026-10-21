package edu.mec.studentportal.notification;

import java.time.LocalDateTime;

/** Immutable in-app notification row. */
public record Notification(
    long id,
    long recipientUserId,
    Long announcementId,
    Long eventId,
    NotificationType notificationType,
    String title,
    String message,
    LocalDateTime createdAt,
    LocalDateTime readAt
) {}
