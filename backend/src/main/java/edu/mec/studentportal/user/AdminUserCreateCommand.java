package edu.mec.studentportal.user;

import java.nio.charset.StandardCharsets;

/** Validated, normalized values for creating one account and its role profile. */
public record AdminUserCreateCommand(
    String fullName,
    String email,
    String password,
    String department,
    Role role,
    UserProfile profile
) {
    private static final int MIN_PASSWORD_BYTES = 8;
    private static final int MAX_PASSWORD_BYTES = 72;

    public AdminUserCreateCommand {
        if (fullName == null) {
            throw new IllegalArgumentException("fullName must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
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

    static AdminUserCreateCommand from(AdminUserCreateRequest request) {
        if (request == null) {
            throw AdminUserValidation.invalid("A request body is required.");
        }
        return new AdminUserCreateCommand(
            AdminUserValidation.requireText(
                request.fullName(),
                "fullName",
                AdminUserValidation.MAX_FULL_NAME_LENGTH
            ),
            AdminUserValidation.requireEmail(request.email()),
            requirePassword(request.password()),
            AdminUserValidation.requireText(
                request.department(),
                "department",
                AdminUserValidation.MAX_DEPARTMENT_LENGTH
            ),
            request.role(),
            AdminUserValidation.parseProfile(
                request.role(),
                request.profile()
            )
        );
    }

    private static String requirePassword(String value) {
        if (value == null || value.isBlank()) {
            throw AdminUserValidation.invalid("password is required.");
        }
        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_PASSWORD_BYTES
            || byteLength > MAX_PASSWORD_BYTES) {
            throw AdminUserValidation.invalid(
                "password must be between " + MIN_PASSWORD_BYTES + " and "
                    + MAX_PASSWORD_BYTES + " UTF-8 bytes."
            );
        }
        return value;
    }
}
