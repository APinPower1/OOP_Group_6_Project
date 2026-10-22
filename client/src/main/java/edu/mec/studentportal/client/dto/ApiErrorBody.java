package edu.mec.studentportal.client.dto;

import java.time.Instant;

/** Standard error body from docs/API_CONTRACT.md; `code` is stable for client handling. */
public record ApiErrorBody(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path
) {}
