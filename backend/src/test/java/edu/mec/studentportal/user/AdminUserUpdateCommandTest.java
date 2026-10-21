package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AdminUserUpdateCommandTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesAndNormalizesValues() throws Exception {
        AdminUserUpdateCommand command = AdminUserUpdateCommand.from(
            request(
                Role.STUDENT,
                """
                {
                  "rollNumber": " R2026001 ",
                  "semester": 3,
                  "batch": " 2026 "
                }
                """
            )
        );

        assertThat(command.fullName()).isEqualTo("Ayesha Khan");
        assertThat(command.email()).isEqualTo("ayesha@example.edu");
        assertThat(command.department()).isEqualTo("Computing");
        assertThat(command.profile())
            .usingRecursiveComparison()
            .isEqualTo(new StudentProfile("R2026001", 3, "2026"));
    }

    @Test
    void defaultsAdminLevelToOne() throws Exception {
        assertThat(AdminUserUpdateCommand.from(
            request(Role.ADMIN, "{}")
        ).profile())
            .usingRecursiveComparison()
            .isEqualTo(new AdminProfile(1));
    }

    @Test
    void rejectsUnsupportedProfileField() {
        assertInvalid(() -> AdminUserUpdateCommand.from(request(
            Role.STUDENT,
            """
            {
              "rollNumber": "R2026001",
              "semester": 3,
              "batch": "2026",
              "employeeId": "E1001"
            }
            """
        )));
    }

    @Test
    void rejectsProfileFieldsFromAnotherRole() {
        assertInvalid(() -> AdminUserUpdateCommand.from(request(
            Role.ORGANIZER,
            """
            {"designation": "Club Coordinator", "batch": "2026"}
            """
        )));
    }

    @Test
    void rejectsBlankFullName() throws Exception {
        assertInvalid(() -> AdminUserUpdateCommand.from(
            new AdminUserUpdateRequest(
                "   ",
                "ayesha@example.edu",
                "Computing",
                Role.ORGANIZER,
                profile("""
                    {"designation": "Club Coordinator"}
                    """)
            )
        ));
    }

    @Test
    void rejectsNullProfile() throws Exception {
        assertInvalid(() -> AdminUserUpdateCommand.from(
            new AdminUserUpdateRequest(
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                Role.ORGANIZER,
                null
            )
        ));
    }

    @Test
    void rejectsNullRequest() {
        assertInvalid(() -> AdminUserUpdateCommand.from(null));
    }

    private static AdminUserUpdateRequest request(
        Role role,
        String profileJson
    ) throws Exception {
        return new AdminUserUpdateRequest(
            "  Ayesha Khan  ",
            "  Ayesha@Example.EDU  ",
            "  Computing  ",
            role,
            profile(profileJson)
        );
    }

    private static JsonNode profile(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private static void assertInvalid(ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown).isInstanceOf(ApiException.class);

        ApiException exception = (ApiException) thrown;
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getCode()).isEqualTo(
            ApiErrorCodes.INVALID_REQUEST
        );
    }
}
