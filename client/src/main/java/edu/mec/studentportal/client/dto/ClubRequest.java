package edu.mec.studentportal.client.dto;

/** Club create/update body; the client never sets id/active flags. */
public record ClubRequest(String name, String description, String category) {}
