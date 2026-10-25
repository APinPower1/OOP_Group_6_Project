package edu.mec.studentportal.user;

/**
 * Admin password reset payload. There is no email-based reset: the admin
 * verifies the user in person (college ID) and sets a fresh temporary password
 * that the user must change on their next login.
 */
public record AdminUserResetPasswordRequest(String newPassword) {
}
