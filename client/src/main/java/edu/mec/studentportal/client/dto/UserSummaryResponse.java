package edu.mec.studentportal.client.dto;

/** Nested user object inside the login response (contract field names). */
public record UserSummaryResponse(
    long id,
    String fullName,
    String email,
    Role role,
    boolean mustChangePassword
) {
    /** Convenience overload: profiles without a pending password change default to {@code false}. */
    public UserSummaryResponse(long id, String fullName, String email, Role role) {
        this(id, fullName, email, role, false);
    }

    public UserSummary toUserSummary() {
        return new UserSummary(id, fullName, email, role, mustChangePassword);
    }
}
