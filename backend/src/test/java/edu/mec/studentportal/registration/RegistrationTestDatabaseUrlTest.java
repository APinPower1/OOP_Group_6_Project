package edu.mec.studentportal.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Database-free unit tests for the {@code TEST_DB_URL} safety guard in
 * {@link RegistrationTestDatabase}. No MySQL connection, no Spring context, no Flyway:
 * this class runs on every build even without a test database. It proves that only the
 * exact {@code student_portal_test} schema is accepted and that diagnostics never leak
 * credentials or query parameters.
 */
class RegistrationTestDatabaseUrlTest {

    private static void assertAccepted(String url) {
        assertThatCode(() -> RegistrationTestDatabase.validateUrl(url))
            .as("URL must be accepted: %s", url)
            .doesNotThrowAnyException();
    }

    private static void assertRejected(String url) {
        assertThatThrownBy(() -> RegistrationTestDatabase.validateUrl(url))
            .as("URL must be rejected: %s", url)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(RegistrationTestDatabase.REQUIRED_SCHEMA)
            .hasMessageContaining(RegistrationTestDatabase.REQUIRED_SCHEMA_EXAMPLE_URL);
    }

    @Test
    void acceptsDefaultUrl() {
        assertAccepted(RegistrationTestDatabase.DEFAULT_URL);
    }

    @Test
    void acceptsUrlWithExtraQueryParameters() {
        assertAccepted("jdbc:mysql://localhost:3306/student_portal_test?serverTimezone=Asia/Kolkata&useSSL=false");
    }

    @Test
    void acceptsUrlWithDifferentHostAndPort() {
        assertAccepted("jdbc:mysql://db.example.internal:3307/student_portal_test");
    }

    @Test
    void acceptsUrlWithoutQueryParameters() {
        assertAccepted("jdbc:mysql://localhost:3306/student_portal_test");
    }

    @Test
    void rejectsMainStudentPortalSchema() {
        assertThatThrownBy(() -> RegistrationTestDatabase.validateUrl(
            "jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be exactly 'student_portal_test'")
            .hasMessageContaining("'student_portal'");
    }

    @Test
    void rejectsSchemaWithSuffixLookalike() {
        assertRejected("jdbc:mysql://localhost:3306/student_portal_test2");
        assertRejected("jdbc:mysql://localhost:3306/student_portal_tests");
    }

    @Test
    void rejectsMissingSchema() {
        assertRejected("jdbc:mysql://localhost:3306/");
        assertRejected("jdbc:mysql://localhost:3306");
    }

    @Test
    void rejectsNestedSchemaPath() {
        assertRejected("jdbc:mysql://localhost:3306/other/student_portal_test");
    }

    @Test
    void rejectsMissingAuthority() {
        assertRejected("jdbc:mysql:///student_portal_test");
    }

    @Test
    void rejectsWrongProtocol() {
        assertRejected("jdbc:postgresql://localhost:3306/student_portal_test");
        assertRejected("mysql://localhost:3306/student_portal_test");
        assertRejected("http://localhost:3306/student_portal_test");
    }

    @Test
    void rejectsMalformedUrls() {
        assertRejected("not a url");
        assertRejected("");
        assertRejected("   ");
        assertRejected(null);
        assertRejected("jdbc:mysql://localhost:not-a-port/student_portal_test");
        assertRejected("jdbc:mysql://localhost:3306/student_portal_test?x=%zz");
    }

    @Test
    void rejectsPortOutOfRange() {
        assertRejected("jdbc:mysql://localhost:70000/student_portal_test");
        assertRejected("jdbc:mysql://localhost:0/student_portal_test");
    }

    @Test
    void rejectsEmbeddedUserinfo() {
        assertThatThrownBy(() -> RegistrationTestDatabase.validateUrl(
            "jdbc:mysql://app_user:s3cret-pw@localhost:3306/student_portal_test"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("userinfo")
            .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("s3cret-pw"));
    }

    @Test
    void diagnosticNeverContainsPasswordOrSensitiveQueryParameters() {
        String url = "jdbc:mysql://localhost:3306/student_portal?password=TopSecret123&user=admin";
        assertThatThrownBy(() -> RegistrationTestDatabase.validateUrl(url))
            .isInstanceOf(IllegalStateException.class)
            .satisfies(exception -> {
                String message = exception.getMessage();
                assertThat(message).doesNotContain("TopSecret123");
                assertThat(message).doesNotContain("student_portal?");
                assertThat(message).doesNotContain(url);
            });
    }

    @Test
    void diagnosticNeverEchoesMalformedRawUrl() {
        String url = "jdbc:mysql://localhost:3306/student_portal_test?password=TopSecret123&x=%zz";
        assertThatThrownBy(() -> RegistrationTestDatabase.validateUrl(url))
            .isInstanceOf(IllegalStateException.class)
            .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("TopSecret123"));
    }

    @Test
    void redactsNonIdentifierSchemaInDiagnostic() {
        String url = "jdbc:mysql://localhost:3306/not%20a%20schema%2Fsecret";
        assertThatThrownBy(() -> RegistrationTestDatabase.validateUrl(url))
            .isInstanceOf(IllegalStateException.class)
            .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("secret"));
    }

    @Test
    void defaultUsernameIsRestrictedTestAccount() {
        assertThat(RegistrationTestDatabase.DEFAULT_USERNAME).isEqualTo("student_portal_test_app");
    }
}
