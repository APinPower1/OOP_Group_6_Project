package edu.mec.studentportal.client.dto;

/** Feedback body; the server sets studentId and submittedAt. */
public record FeedbackRequest(int rating, String comments) {}
