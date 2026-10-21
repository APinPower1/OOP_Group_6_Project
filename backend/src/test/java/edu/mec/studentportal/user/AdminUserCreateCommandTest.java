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

class AdminUserCreateCommandTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STUDENT_PROFILE =
        """
        {"rollNumber": "R2026001", "semester": 3, "batch": "2026"}
        """;

    @Test
    void parsesStudentProfileAndNormalizesAccountValues() throws Exception {
        AdminUserCreateCommand command = AdminUserCreateCommand.from(
            new AdminUserCreateRequest(
                "  Ayesha Khan  ",
                "  Ayesha@Example.EDU  ",
                "initial-password",
                "  Computing  ",
                Role.STUDENT,
                profile("""
                    {
                      "rollNumber": " R2026001 ",
                      "semester": 3,
                      "batch": " 2026 "
                    }
                    """)
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
    void parsesOrganizerProfile() throws Exception {
        assertThat(fromProfile("ORGANIZER", """
            {"designation": "Club Coordinator"}
            """).profile())
            .usingRecursiveComparison()
            .isEqualTo(new OrganizerProfile("Club Coordinator"));
    }

    @Test
    void parsesFacultyProfile() throws Exception {
        assertThat(fromProfile("FACULTY", """
            {"employeeId": "E1001", "designation": "Professor"}
            """).profile())
            .usingRecursiveComparison()
            .isEqualTo(new FacultyProfile("E1001", "Professor"));
    }

    @Test
    void defaultsAdminLevelToOne() throws Exception {
        assertThat(fromProfile("ADMIN", "{}").profile())
            .usingRecursiveComparison()
            .isEqualTo(new AdminProfile(1));
    }

    @Test
    void parsesExplicitAdminLevel() throws Exception {
        assertThat(fromProfile("ADMIN", """
            {"adminLevel": 3}
            """).profile())
            .usingRecursiveComparison()
            .isEqualTo(new AdminProfile(3));
    }

    @Test
    void rejectsUnsupportedProfileField() {
        assertInvalid(() -> fromProfile("STUDENT", """
            {
              "rollNumber": "R2026001",
              "semester": 3,
              "batch": "2026",
              "employeeId": "E1001"
            }
            """));
    }

    @Test
    void rejectsProfileFieldFromAnotherRole() {
        assertInvalid(() -> fromProfile("ORGANIZER", """
            {"designation": "Club Coordinator", "batch": "2026"}
            """));
    }

    @Test
    void rejectsMissingRequiredProfileField() {
        assertInvalid(() -> fromProfile("STUDENT", """
            {"rollNumber": "R2026001", "semester": 3}
            """));
    }

    @Test
    void rejectsNullProfileFieldValue() {
        assertInvalid(() -> fromProfile("FACULTY", """
            {"employeeId": "E1001", "designation": null}
            """));
    }

    @Test
    void rejectsNonObjectProfile() {
        assertInvalid(() -> fromProfile("ORGANIZER", "\"Club Coordinator\""));
    }

    @Test
    void rejectsNullProfile() {
        assertInvalid(() -> AdminUserCreateCommand.from(
            new AdminUserCreateRequest(
                "Ayesha Khan",
                "ayesha@example.edu",
                "initial-password",
                "Computing",
                Role.STUDENT,
                null
            )
        ));
    }

    @Test
    void rejectsBlankProfileText() {
        assertInvalid(() -> fromProfile("ORGANIZER", """
            {"designation": "   "}
            """));
    }

    @Test
    void rejectsTooLongProfileText() {
        assertInvalid(() -> fromProfile("ORGANIZER", """
            {"designation": "%s"}
            """.formatted("x".repeat(101))));
    }

    @Test
    void rejectsSemesterBelowOne() {
        assertInvalid(() -> fromProfile("STUDENT", """
            {"rollNumber": "R2026001", "semester": 0, "batch": "2026"}
            """));
    }

    @Test
    void rejectsNonIntegerSemester() {
        assertInvalid(() -> fromProfile("STUDENT", """
            {"rollNumber": "R2026001", "semester": "3", "batch": "2026"}
            """));
    }

    @Test
    void rejectsAdminLevelBelowOne() {
        assertInvalid(() -> fromProfile("ADMIN", """
            {"adminLevel": 0}
            """));
    }

    @Test
    void rejectsNonIntegerAdminLevel() {
        assertInvalid(() -> fromProfile("ADMIN", """
            {"adminLevel": 1.5}
            """));
    }

    @Test
    void rejectsPasswordShorterThanEightBytes() {
        assertInvalid(() -> AdminUserCreateCommand.from(
            requestWithPassword("short")
        ));
    }

    @Test
    void rejectsPasswordLongerThanSeventyTwoBytes() {
        assertInvalid(() -> AdminUserCreateCommand.from(
            requestWithPassword("x".repeat(73))
        ));
    }

    @Test
    void rejectsMultiBytePasswordOverSeventyTwoBytes() {
        assertInvalid(() -> AdminUserCreateCommand.from(
            requestWithPassword("\u00e9".repeat(37))
        ));
    }

    @Test
    void acceptsPasswordAtSeventyTwoByteLimit() throws Exception {
        assertThat(AdminUserCreateCommand.from(
            requestWithPassword("\u00e9".repeat(36))
        ).password()).hasSize(36);
    }

    @Test
    void rejectsBlankFullName() {
        assertInvalid(() -> AdminUserCreateCommand.from(
            new AdminUserCreateRequest(
                "   ",
                "ayesha@example.edu",
                "initial-password",
                "Computing",
                Role.STUDENT,
                profile(STUDENT_PROFILE)
            )
        ));
    }

    @Test
    void rejectsTooLongFullName() {
        assertInvalid(() -> AdminUserCreateCommand.from(
            new AdminUserCreateRequest(
                "x".repeat(121),
                "ayesha@example.edu",
                "initial-password",
                "Computing",
                Role.STUDENT,
                profile(STUDENT_PROFILE)
            )
        ));
    }

    @Test
    void rejectsTooLongEmail() {
        assertInvalid(() -> AdminUserCreateCommand.from(
            new AdminUserCreateRequest(
                "Ayesha Khan",
                "x".repeat(243) + "@example.edu",
                "initial-password",
                "Computing",
                Role.STUDENT,
                profile(STUDENT_PROFILE)
            )
        ));
    }

    @Test
    void rejectsTooLongDepartment() {
        assertInvalid(() -> AdminUserCreateCommand.from(
            new AdminUserCreateRequest(
                "Ayesha Khan",
                "ayesha@example.edu",
                "initial-password",
                "x".repeat(121),
                Role.STUDENT,
                profile(STUDENT_PROFILE)
            )
        ));
    }

    @Test
    void rejectsNullRole() {
        assertInvalid(() -> AdminUserCreateCommand.from(
            new AdminUserCreateRequest(
                "Ayesha Khan",
                "ayesha@example.edu",
                "initial-password",
                "Computing",
                null,
                profile(STUDENT_PROFILE)
            )
        ));
    }

    @Test
    void rejectsNullRequest() {
        assertInvalid(() -> AdminUserCreateCommand.from(null));
    }

    private static AdminUserCreateCommand fromProfile(
        String role,
        String profileJson
    ) throws Exception {
        return AdminUserCreateCommand.from(new AdminUserCreateRequest(
            "Ayesha Khan",
            "ayesha@example.edu",
            "initial-password",
            "Computing",
            Role.valueOf(role),
            profile(profileJson)
        ));
    }

    private static AdminUserCreateRequest requestWithPassword(String password)
        throws Exception {
        return new AdminUserCreateRequest(
            "Ayesha Khan",
            "ayesha@example.edu",
            password,
            "Computing",
            Role.STUDENT,
            profile(STUDENT_PROFILE)
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
