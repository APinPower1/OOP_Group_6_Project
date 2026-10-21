package edu.mec.studentportal.auth;

import edu.mec.studentportal.user.Role;

public record UserSummaryResponse(
    long id,
    String fullName,
    String email,
    Role role
) {
    public UserSummaryResponse {
        if (fullName == null) {
            throw new IllegalArgumentException("fullName must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
    }
}
