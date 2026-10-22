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
 * <p>Under the production JWT chain the principal is Member 2's {@link UserSecurityState} record,
 * whose {@code toString()} is not numeric, so the id is taken from the record directly. The
 * numeric-string fallback covers the test auth filter, which authenticates with the raw user id
 * as the principal.
 */
public final class CurrentUser {
    private CurrentUser() {}

    public static long id(Authentication authentication) {
        if (authentication == null) {
            throw unauthenticated();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserSecurityState state) {
            return state.id();
        }
        String name = authentication.getName();
        if (name == null) {
            throw unauthenticated();
        }
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
    }

    private static ApiException unauthenticated() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCodes.UNAUTHENTICATED,
            "Authentication is required."
        );
    }
}
