package edu.mec.studentportal.report;

import java.util.List;

/**
 * Response for GET /api/admin/reports/participation: counts by status plus a per-event
 * participation table (top 50 by confirmed seats). Ratings are exposed only as aggregates.
 */
public record ParticipationReportResponse(
    EventCounts events,
    RegistrationCounts registrations,
    FeedbackSummary feedback,
    List<ParticipationRow> participation
) {
    public ParticipationReportResponse {
        if (events == null || registrations == null || feedback == null) {
            throw new IllegalArgumentException("summary sections must not be null");
        }
        participation = participation == null ? List.of() : List.copyOf(participation);
    }

    public record EventCounts(
        long total,
        long pendingApproval,
        long approved,
        long rejected,
        long cancelled
    ) {}

    public record RegistrationCounts(long total, long confirmed, long cancelled) {}

    /** averageRating is null when no feedback exists yet. */
    public record FeedbackSummary(long count, Double averageRating) {}

    /** averageRating is null when the event has no feedback yet. */
    public record ParticipationRow(
        long eventId,
        String title,
        String status,
        int capacity,
        long confirmedCount,
        long cancelledCount,
        long feedbackCount,
        Double averageRating
    ) {}
}
