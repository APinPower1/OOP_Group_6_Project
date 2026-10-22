package edu.mec.studentportal.client.dto;

import java.util.Map;

/** Admin create-user body; profile must match role fields exactly (unsupported keys = 400). */
public record AdminUserCreateRequest(
    String fullName,
    String email,
    String password,
    String department,
    Role role,
    Map<String, Object> profile
) {}
