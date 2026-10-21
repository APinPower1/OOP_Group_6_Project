package edu.mec.studentportal.user;

record AdminUserRow(
    long id,
    String fullName,
    String email,
    String department,
    Role role,
    boolean enabled,
    UserProfile profile
) {
    AdminUserRow {
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
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
    }
}
