package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import edu.mec.studentportal.auth.LoginRequest;
import org.junit.jupiter.api.Test;

class AdminUserCreateRequestTest {
    private static final String VALID_BODY = """
        {
          "fullName": "Ayesha Khan",
          "email": "ayesha@example.edu",
          "password": "initial-password",
          "department": "Computing",
          "role": "STUDENT",
          "profile": {
            "rollNumber": "R2026001",
            "semester": 3,
            "batch": "2026"
          }
        }
        """;

    private final ObjectMapper mapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    @Test
    void readsAllowedFields() throws Exception {
        AdminUserCreateRequest request = mapper.readValue(
            VALID_BODY,
            AdminUserCreateRequest.class
        );

        assertThat(request.fullName()).isEqualTo("Ayesha Khan");
        assertThat(request.email()).isEqualTo("ayesha@example.edu");
        assertThat(request.role()).isEqualTo(Role.STUDENT);
        assertThat(request.profile().get("rollNumber").asText())
            .isEqualTo("R2026001");
    }

    @Test
    void rejectsUnsupportedTopLevelFieldEvenWhenMapperIgnoresUnknowns() {
        assertThatThrownBy(() -> mapper.readValue(
            VALID_BODY.replace(
                "\"role\": \"STUDENT\"",
                "\"role\": \"STUDENT\", \"enabled\": true"
            ),
            AdminUserCreateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("Unsupported property")
            .hasMessageContaining("enabled");
    }

    @Test
    void rejectsArbitraryUnsupportedTopLevelField() {
        assertThatThrownBy(() -> mapper.readValue(
            VALID_BODY.replace(
                "\"role\": \"STUDENT\"",
                "\"role\": \"STUDENT\", \"nickname\": \"Ash\""
            ),
            AdminUserCreateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("nickname");
    }

    @Test
    void rejectsNonObjectBody() {
        assertThatThrownBy(() -> mapper.readValue(
            "[]",
            AdminUserCreateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("JSON object");
    }

    @Test
    void rejectsNonStringAccountField() {
        assertThatThrownBy(() -> mapper.readValue(
            VALID_BODY.replace(
                "\"Ayesha Khan\"",
                "42"
            ),
            AdminUserCreateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("fullName");
    }

    @Test
    void rejectsUnknownRole() {
        assertThatThrownBy(() -> mapper.readValue(
            VALID_BODY.replace(
                "\"role\": \"STUDENT\"",
                "\"role\": \"TEACHER\""
            ),
            AdminUserCreateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("Unknown role");
    }

    @Test
    void keepsDefaultUnknownPropertyBehaviorForLogin() throws Exception {
        LoginRequest request = mapper.readValue("""
            {
              "email": "student@example.edu",
              "password": "demo-password",
              "rememberMe": true
            }
            """, LoginRequest.class);

        assertThat(request.email()).isEqualTo("student@example.edu");
        assertThat(request.password()).isEqualTo("demo-password");
    }
}
