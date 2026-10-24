package edu.mec.studentportal.client.dto;

/** POST /admin/users/{id}/reset-password body; never returns the plaintext. */
public record AdminResetPasswordRequest(String newPassword) {}
