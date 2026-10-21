package edu.mec.studentportal.feedback;

import java.time.LocalDateTime;
import java.util.List;

/** Response DTOs for the feedback endpoints in docs/API_CONTRACT.md. */
public final class FeedbackResponses {
    private FeedbackResponses() {}

    /** Owner/staff view: full feedback record including student identity. */
    public record FeedbackResponse(
        long id,
        long studentId,
        long eventId,
        int rating,
        String comments,
        LocalDateTime submittedAt
    ) {
        static FeedbackResponse from(Feedback feedback) {
            return new FeedbackResponse(
                feedback.id(),
                feedback.studentId(),
                feedback.eventId(),
                feedback.rating(),
                feedback.comments(),
                feedback.submittedAt()
            );
        }
    }

    /** General viewer summary: average rating and count only, never student identity. */
    public record FeedbackSummaryResponse(long eventId, double averageRating, long ratingCount) {}

    /** Private in-module row for the summary query. */
    public record RatingRow(int rating) {}
}
