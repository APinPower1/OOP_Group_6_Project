package edu.mec.studentportal.auth;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    UserSummaryResponse user
) {
    public static final String BEARER_TOKEN_TYPE = "Bearer";

    public LoginResponse {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        if (!BEARER_TOKEN_TYPE.equals(tokenType)) {
            throw new IllegalArgumentException("tokenType must be Bearer");
        }
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException(
                "expiresInSeconds must be greater than zero"
            );
        }
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
    }

    public static LoginResponse bearer(
        String accessToken,
        long expiresInSeconds,
        UserSummaryResponse user
    ) {
        return new LoginResponse(
            accessToken,
            BEARER_TOKEN_TYPE,
            expiresInSeconds,
            user
        );
    }
}
