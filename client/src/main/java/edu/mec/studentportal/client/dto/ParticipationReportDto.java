package edu.mec.studentportal.client.dto;

import java.util.List;

/** GET /admin/reports/participation response (D-018); ratings null when no feedback exists. */
public record ParticipationReportDto(
    EventCounts events,
    RegistrationCounts registrations,
    FeedbackSummary feedback,
    List<ParticipationRow> participation
) {
    public ParticipationReportDto {
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

    public record FeedbackSummary(long count, Double averageRating) {}

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
