package edu.mec.studentportal.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Raw full-replacement update body; profile fields are validated by the command. */
@JsonDeserialize(using = AdminUserUpdateRequestDeserializer.class)
public record AdminUserUpdateRequest(
    @NotBlank @Size(max = 120) String fullName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 120) String department,
    @NotNull Role role,
    @NotNull JsonNode profile
) {
}
