package edu.mec.studentportal.common;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/**
 * Resolves the server-derived acting user id from the authenticated principal. Controllers must
 * use this instead of trusting any client-supplied user id. The principal name is expected to be
 * the numeric user id carried in the JWT subject (Member 2's auth contract).
 */
public final class CurrentUser {
    private CurrentUser() {}

    public static long id(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCodes.UNAUTHENTICATED,
                "Authentication is required."
            );
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCodes.UNAUTHENTICATED,
                "Authentication is required."
            );
        }
    }
}
