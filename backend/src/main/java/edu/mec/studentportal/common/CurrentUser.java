package edu.mec.studentportal.common;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.user.UserSecurityState;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/**
 * Resolves the server-derived acting user id from the authenticated principal. Controllers must
 * use this instead of trusting any client-supplied user id.
 */
public final class CurrentUser {
    private CurrentUser() {}

    public static long id(Authentication authentication) {
        if (authentication == null) {
            throw authenticationRequired();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserSecurityState user) {
            return requirePositiveId(user.id());
        }
        if (!(principal instanceof String subject)) {
            throw authenticationRequired();
        }

        try {
            return requirePositiveId(Long.parseLong(subject));
        } catch (NumberFormatException exception) {
            throw authenticationRequired();
        }
    }

    private static long requirePositiveId(long userId) {
        if (userId <= 0) {
            throw authenticationRequired();
        }
        return userId;
    }

    private static ApiException authenticationRequired() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCodes.UNAUTHENTICATED,
            "Authentication is required."
        );
    }
}
