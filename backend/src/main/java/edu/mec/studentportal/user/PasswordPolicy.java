package edu.mec.studentportal.user;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/**
 * Single source of truth for the new-password rule (8–72 UTF-8 bytes, the BCrypt
 * limit). Used by admin creation, admin password reset and self-service change.
 */
public final class PasswordPolicy {
    public static final int MIN_PASSWORD_BYTES = 8;
    public static final int MAX_PASSWORD_BYTES = 72;

    private PasswordPolicy() {}

    /** Validates and returns the raw password, or throws a 400 INVALID_REQUEST. */
    public static String requireNewPassword(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("password is required.");
        }
        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_PASSWORD_BYTES || byteLength > MAX_PASSWORD_BYTES) {
            throw invalid(
                "password must be between " + MIN_PASSWORD_BYTES + " and "
                    + MAX_PASSWORD_BYTES + " UTF-8 bytes."
            );
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.INVALID_REQUEST,
            message
        );
    }
}
