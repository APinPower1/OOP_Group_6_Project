package edu.mec.studentportal.feedback;

import java.time.LocalDateTime;

/** Immutable feedback domain object. */
public record Feedback(
    long id,
    long studentId,
    long eventId,
    int rating,
    String comments,
    LocalDateTime submittedAt
) {}
