package edu.mec.studentportal.client.dto;

/** Aggregate feedback view: average and count only, never student identity. */
public record FeedbackSummaryDto(long eventId, double averageRating, long ratingCount) {}
