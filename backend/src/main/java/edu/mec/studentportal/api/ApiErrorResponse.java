package edu.mec.studentportal.api;

import java.time.Instant;

/** The common JSON error shape documented in docs/API_CONTRACT.md. */
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path
) {
    public static ApiErrorResponse of(int status, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path);
    }
}