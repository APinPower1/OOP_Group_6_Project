package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Owner/staff feedback view: full record including student identity. */
public record FeedbackDto(
    long id,
    long studentId,
    long eventId,
    int rating,
    String comments,
    LocalDateTime submittedAt
) {}
