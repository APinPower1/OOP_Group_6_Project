package edu.mec.studentportal.client.dto;

/** Rejection body; a non-blank reason is mandatory. */
public record RejectRequest(String reason) {}
