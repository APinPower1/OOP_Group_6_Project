package edu.mec.studentportal.client.dto;

/** Nested user object inside the login response (contract field names). */
public record UserSummaryResponse(
    long id,
    String fullName,
    String email,
    Role role,
    boolean mustChangePassword
) {
    public UserSummary toUserSummary() {
        return new UserSummary(id, fullName, email, role, mustChangePassword);
    }
}
