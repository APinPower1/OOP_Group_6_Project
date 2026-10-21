package edu.mec.studentportal.user;

/**
 * Current authorization state for a user, loaded per request so that role or
 * enabled-status changes take effect immediately for existing tokens.
 */
public record UserSecurityState(long id, Role role, boolean enabled) {
    public UserSecurityState {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
    }
}
