package edu.mec.studentportal.common;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.user.UserSecurityState;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/**
 * Resolves the server-derived acting user id from the authenticated principal. Controllers must
 * use this instead of trusting any client-supplied user id.
 *
 * <p>Under the production JWT chain the principal is a {@link UserSecurityState}; the numeric
 * string fallback supports test authentication filters that use the user id as the principal.
 */
public final class CurrentUser {
    private CurrentUser() {}

    public static long id(Authentication authentication) {
        if (authentication == null) {
            throw unauthenticated();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserSecurityState state) {
            return requirePositiveId(state.id());
        }
        if (!(principal instanceof String subject)) {
            throw unauthenticated();
        }

        try {
            return requirePositiveId(Long.parseLong(subject));
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
    }

    private static long requirePositiveId(long userId) {
        if (userId <= 0) {
            throw unauthenticated();
        }
        return userId;
    }

    private static ApiException unauthenticated() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCodes.UNAUTHENTICATED,
            "Authentication is required."
        );
    }
}
