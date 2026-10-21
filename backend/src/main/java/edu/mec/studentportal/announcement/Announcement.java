package edu.mec.studentportal.announcement;

import java.time.LocalDateTime;

/**
 * Immutable announcement domain object. It exposes its notification payload
 * ({@link #notificationTitle()} / {@link #notificationMessage()}) so a NotificationService can
 * fan out in-app notifications; the domain object never touches JDBC.
 */
public record Announcement(
    long id,
    String title,
    String content,
    long createdBy,
    String audienceRole,
    AnnouncementStatus status,
    LocalDateTime createdAt,
    LocalDateTime publishedAt,
    LocalDateTime archivedAt
) {
    /** Title shown on the generated in-app notification row. */
    public String notificationTitle() {
        return title;
    }

    /** Message body shown on the generated in-app notification row. */
    public String notificationMessage() {
        return content;
    }
}
