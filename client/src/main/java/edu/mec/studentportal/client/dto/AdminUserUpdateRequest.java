package edu.mec.studentportal.client.dto;

import java.util.Map;

/** Admin full-replacement update body; password and enabled are not accepted (D-016). */
public record AdminUserUpdateRequest(
    String fullName,
    String email,
    String department,
    Role role,
    Map<String, Object> profile
) {}
