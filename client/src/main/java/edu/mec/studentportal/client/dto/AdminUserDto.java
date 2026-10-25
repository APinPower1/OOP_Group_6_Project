package edu.mec.studentportal.client.dto;

import java.util.Map;

/** Admin user representation; profile fields depend on role (see API_CONTRACT.md). */
public record AdminUserDto(
    long id,
    String fullName,
    String email,
    String department,
    Role role,
    boolean enabled,
    String passwordHash,
    Map<String, Object> profile
) {
    public AdminUserDto {
        profile = profile == null ? Map.of() : Map.copyOf(profile);
    }
}
