package edu.mec.studentportal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mec.studentportal.user.Role;
import org.junit.jupiter.api.Test;

class LoginResponseTest {
    private static final UserSummaryResponse USER = new UserSummaryResponse(
        41L,
        "Demo Student",
        "student@example.edu",
        Role.STUDENT
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesResponseFields() {
        LoginResponse response = new LoginResponse(
            "signed-token",
            "Bearer",
            1800L,
            USER
        );

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(1800L);
        assertThat(response.user()).isEqualTo(USER);
    }

    @Test
    void bearerFactoryUsesBearerTokenType() {
        LoginResponse response = LoginResponse.bearer("signed-token", 1800L, USER);

        assertThat(response.tokenType()).isEqualTo(LoginResponse.BEARER_TOKEN_TYPE);
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void rejectsBlankAccessToken() {
        assertThatThrownBy(
            () -> new LoginResponse("   ", "Bearer", 1800L, USER)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("accessToken must not be blank");
    }

    @Test
    void rejectsNonBearerTokenType() {
        assertThatThrownBy(
            () -> new LoginResponse("signed-token", "Basic", 1800L, USER)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("tokenType must be Bearer");
    }

    @Test
    void rejectsNonPositiveExpiry() {
        assertThatThrownBy(
            () -> LoginResponse.bearer("signed-token", 0L, USER)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expiresInSeconds must be greater than zero");
    }

    @Test
    void rejectsMissingUser() {
        assertThatThrownBy(
            () -> LoginResponse.bearer("signed-token", 1800L, null)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("user must not be null");
    }

    @Test
    void serializesContractFieldsWithoutSensitiveData() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(
            LoginResponse.bearer("signed-token", 1800L, USER)
        );
        JsonNode body = OBJECT_MAPPER.readTree(json);

        assertThat(body.size()).isEqualTo(4);
        assertThat(body.get("accessToken").asText()).isEqualTo("signed-token");
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresInSeconds").asLong()).isEqualTo(1800L);

        JsonNode user = body.get("user");
        assertThat(user.size()).isEqualTo(4);
        assertThat(user.get("id").asLong()).isEqualTo(41L);
        assertThat(user.get("fullName").asText()).isEqualTo("Demo Student");
        assertThat(user.get("email").asText()).isEqualTo("student@example.edu");
        assertThat(user.get("role").asText()).isEqualTo("STUDENT");
        assertThat(json).doesNotContain("passwordHash", "password");
    }
}
