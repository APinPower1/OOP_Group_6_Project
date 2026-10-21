package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class AdminUserUpdateRequestTest {
    private static final String VALID_BODY = """
        {
          "fullName": "Ayesha Khan",
          "email": "ayesha@example.edu",
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
        AdminUserUpdateRequest request = mapper.readValue(
            VALID_BODY,
            AdminUserUpdateRequest.class
        );

        assertThat(request.fullName()).isEqualTo("Ayesha Khan");
        assertThat(request.email()).isEqualTo("ayesha@example.edu");
        assertThat(request.role()).isEqualTo(Role.STUDENT);
        assertThat(request.profile().get("rollNumber").asText())
            .isEqualTo("R2026001");
    }

    @Test
    void rejectsPasswordFieldEvenWhenMapperIgnoresUnknowns() {
        assertThatThrownBy(() -> mapper.readValue(
            VALID_BODY.replace(
                "\"role\": \"STUDENT\"",
                "\"role\": \"STUDENT\", \"password\": \"initial-password\""
            ),
            AdminUserUpdateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("Unsupported property")
            .hasMessageContaining("password");
    }

    @Test
    void rejectsEnabledField() {
        assertThatThrownBy(() -> mapper.readValue(
            VALID_BODY.replace(
                "\"role\": \"STUDENT\"",
                "\"role\": \"STUDENT\", \"enabled\": false"
            ),
            AdminUserUpdateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("enabled");
    }

    @Test
    void rejectsArbitraryUnsupportedTopLevelField() {
        assertThatThrownBy(() -> mapper.readValue(
            VALID_BODY.replace(
                "\"role\": \"STUDENT\"",
                "\"role\": \"STUDENT\", \"nickname\": \"Ash\""
            ),
            AdminUserUpdateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("nickname");
    }

    @Test
    void rejectsNonObjectBody() {
        assertThatThrownBy(() -> mapper.readValue(
            "[]",
            AdminUserUpdateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("JSON object");
    }

    @Test
    void rejectsNonStringAccountField() {
        assertThatThrownBy(() -> mapper.readValue(
            VALID_BODY.replace("\"Ayesha Khan\"", "42"),
            AdminUserUpdateRequest.class
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
            AdminUserUpdateRequest.class
        ))
            .isInstanceOf(JsonProcessingException.class)
            .hasMessageContaining("Unknown role");
    }
}
