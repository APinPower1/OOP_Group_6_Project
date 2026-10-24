package edu.mec.studentportal.client.dto;

/** POST /auth/change-password body; the server verifies the current password. */
public record ChangePasswordRequest(String currentPassword, String newPassword) {}
