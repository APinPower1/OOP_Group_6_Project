package edu.mec.studentportal.feedback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Feedback request per docs/API_CONTRACT.md. The server sets studentId and submittedAt;
 * the client never sends them.
 */
public record FeedbackRequest(
    @Min(value = 1, message = "rating must be between 1 and 5")
    @Max(value = 5, message = "rating must be between 1 and 5")
    int rating,
    @Size(max = 2000) String comments
) {}
