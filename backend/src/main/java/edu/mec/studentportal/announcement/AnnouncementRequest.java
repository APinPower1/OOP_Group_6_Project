package edu.mec.studentportal.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Publish request per docs/API_CONTRACT.md. {@code audienceRole} is null (all enabled users) or
 * exactly one of the four roles; the client never supplies creator/status/timestamps.
 */
public record AnnouncementRequest(
    @NotBlank @Size(max = 160) String title,
    @NotBlank String content,
    @Pattern(
        regexp = "STUDENT|ORGANIZER|FACULTY|ADMIN",
        message = "audienceRole must be one of STUDENT, ORGANIZER, FACULTY, ADMIN or null"
    )
    String audienceRole
) {}
