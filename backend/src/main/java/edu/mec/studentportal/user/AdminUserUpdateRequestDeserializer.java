package edu.mec.studentportal.user;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Strict deserialization for the update-user body; password is not accepted. */
final class AdminUserUpdateRequestDeserializer
    extends AdminUserRequestDeserializer<AdminUserUpdateRequest> {
    AdminUserUpdateRequestDeserializer() {
        super(Set.of(
            "fullName",
            "email",
            "department",
            "role",
            "profile"
        ));
    }

    @Override
    protected AdminUserUpdateRequest build(
        String fullName,
        String email,
        String password,
        String department,
        Role role,
        JsonNode profile
    ) {
        return new AdminUserUpdateRequest(
            fullName,
            email,
            department,
            role,
            profile
        );
    }
}
