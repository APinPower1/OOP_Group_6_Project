package edu.mec.studentportal.announcement;

import java.time.LocalDateTime;

/** Announcement response per docs/API_CONTRACT.md. */
public record AnnouncementResponse(
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
    static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(
            announcement.id(),
            announcement.title(),
            announcement.content(),
            announcement.createdBy(),
            announcement.audienceRole(),
            announcement.status(),
            announcement.createdAt(),
            announcement.publishedAt(),
            announcement.archivedAt()
        );
    }
}
