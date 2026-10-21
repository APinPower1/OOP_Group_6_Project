package edu.mec.studentportal.user;

/**
 * Read-only authentication projection of a {@code users} row.
 *
 * <p>Contains the password hash for server-side verification only. Never
 * serialize or log an instance of this record.
 */
public record UserAccount(
    long id,
    String fullName,
    String email,
    String passwordHash,
    String department,
    Role role,
    boolean enabled
) {
    public UserAccount {
        if (fullName == null) {
            throw new IllegalArgumentException("fullName must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("passwordHash must not be null");
        }
        if (department == null) {
            throw new IllegalArgumentException("department must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
    }
}
