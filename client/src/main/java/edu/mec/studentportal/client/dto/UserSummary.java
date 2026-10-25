package edu.mec.studentportal.client.dto;

/** Signed-in user summary held in memory by {@code SessionContext}. */
public record UserSummary(
    long id,
    String fullName,
    String email,
    Role role,
    boolean mustChangePassword
) {
    public UserSummary {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
    }
}
