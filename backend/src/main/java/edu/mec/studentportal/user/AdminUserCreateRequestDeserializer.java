package edu.mec.studentportal.user;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Strict deserialization for the create-user body. */
final class AdminUserCreateRequestDeserializer
    extends AdminUserRequestDeserializer<AdminUserCreateRequest> {
    AdminUserCreateRequestDeserializer() {
        super(Set.of(
            "fullName",
            "email",
            "password",
            "department",
            "role",
            "profile"
        ));
    }

    @Override
    protected AdminUserCreateRequest build(
        String fullName,
        String email,
        String password,
        String department,
        Role role,
        JsonNode profile
    ) {
        return new AdminUserCreateRequest(
            fullName,
            email,
            password,
            department,
            role,
            profile
        );
    }
}
