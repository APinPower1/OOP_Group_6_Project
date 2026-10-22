package edu.mec.studentportal.client.dto;

/** Response for POST /auth/login; token lives only in the in-memory SessionContext. */
public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    UserSummaryResponse user
) {}
