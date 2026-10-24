package edu.mec.studentportal.user;

public record AdminUserResponse(
    long id,
    String fullName,
    String email,
    String department,
    Role role,
    boolean enabled,
    String passwordHash,
    UserProfile profile
) {
    public AdminUserResponse {
        if (fullName == null) {
            throw new IllegalArgumentException("fullName must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (department == null) {
            throw new IllegalArgumentException("department must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("passwordHash must not be null");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
    }

    static AdminUserResponse from(AdminUserRow row) {
        return new AdminUserResponse(
            row.id(),
            row.fullName(),
            row.email(),
            row.department(),
            row.role(),
            row.enabled(),
            row.passwordHash(),
            row.profile()
        );
    }
}
