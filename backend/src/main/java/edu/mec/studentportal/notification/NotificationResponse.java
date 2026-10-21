package edu.mec.studentportal.notification;

import java.time.LocalDateTime;

/** Notification response per docs/API_CONTRACT.md. */
public record NotificationResponse(
    long id,
    NotificationType notificationType,
    String title,
    String message,
    Long announcementId,
    Long eventId,
    LocalDateTime createdAt,
    LocalDateTime readAt
) {
    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.id(),
            notification.notificationType(),
            notification.title(),
            notification.message(),
            notification.announcementId(),
            notification.eventId(),
            notification.createdAt(),
            notification.readAt()
        );
    }
}
