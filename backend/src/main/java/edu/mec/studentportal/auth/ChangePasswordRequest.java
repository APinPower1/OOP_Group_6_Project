package edu.mec.studentportal.auth;

/**
 * Self-service password change: the user proves they know the current password,
 * then sets a new one (8–72 UTF-8 bytes — validated by the service).
 */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
