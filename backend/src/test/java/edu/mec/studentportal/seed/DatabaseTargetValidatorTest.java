package edu.mec.studentportal.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Database-free unit tests for {@link DatabaseTargetValidator}. No MySQL, no Spring context, no
 * environment variables: these tests run on every build and prove that only the exact
 * {@code student_portal} schema is accepted, and that diagnostics never leak credentials.
 */
class DatabaseTargetValidatorTest {

    private static void assertAccepted(String url) {
        assertThatCode(() -> DatabaseTargetValidator.validate(url))
            .as("URL must be accepted: %s", url)
            .doesNotThrowAnyException();
    }

    private static void assertRejected(String url) {
        assertThatThrownBy(() -> DatabaseTargetValidator.validate(url))
            .as("URL must be rejected: %s", url)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(DatabaseTargetValidator.REQUIRED_SCHEMA)
            .hasMessageContaining(DatabaseTargetValidator.REQUIRED_SCHEMA_EXAMPLE_URL);
    }

    @Test
    void acceptsValidApplicationUrl() {
        assertAccepted(
            "jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata");
    }

    @Test
    void acceptsUrlWithExtraQueryParameters() {
        assertAccepted(
            "jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata&useSSL=false");
    }

    @Test
    void acceptsUrlWithDifferentHostAndPort() {
        assertAccepted("jdbc:mysql://db.example.internal:3307/student_portal");
    }

    @Test
    void acceptsUrlWithoutQueryParameters() {
        assertAccepted("jdbc:mysql://localhost:3306/student_portal");
    }

    @Test
    void localBootstrapAcceptsOnlyLoopbackHosts() {
        assertThat(DatabaseTargetValidator.validateLocal(
            "jdbc:mysql://localhost:3306/student_portal"))
            .isEqualTo("jdbc:mysql://localhost:3306/student_portal");
        assertThat(DatabaseTargetValidator.validateLocal(
            "jdbc:mysql://127.0.0.1:3306/student_portal"))
            .isEqualTo("jdbc:mysql://127.0.0.1:3306/student_portal");
        assertThatThrownBy(() -> DatabaseTargetValidator.validateLocal(
            "jdbc:mysql://db.example.internal:3306/student_portal"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("local loopback");
    }

    @Test
    void returnsValidatedUrlUnchanged() {
        String url = "jdbc:mysql://localhost:3306/student_portal";
        assertThat(DatabaseTargetValidator.validate(url)).isEqualTo(url);
    }

    @Test
    void rejectsTestSchema() {
        assertThatThrownBy(() -> DatabaseTargetValidator.validate(
            "jdbc:mysql://localhost:3306/student_portal_test?serverTimezone=Asia/Kolkata"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be exactly 'student_portal'")
            .hasMessageContaining("'student_portal_test'");
    }

    @Test
    void rejectsArbitrarySchema() {
        assertThatThrownBy(() -> DatabaseTargetValidator.validate(
            "jdbc:mysql://localhost:3306/some_other_db"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be exactly 'student_portal'")
            .hasMessageContaining("'some_other_db'");
    }

    @Test
    void rejectsSchemaWithSuffixLookalike() {
        assertRejected("jdbc:mysql://localhost:3306/student_portal_backup");
        assertRejected("jdbc:mysql://localhost:3306/student_portals");
    }

    @Test
    void rejectsEmbeddedUsernameAndPassword() {
        assertThatThrownBy(() -> DatabaseTargetValidator.validate(
            "jdbc:mysql://app_user:s3cret-pw@localhost:3306/student_portal"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userinfo")
            .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("s3cret-pw"));
    }

    @Test
    void rejectsMalformedUrls() {
        assertRejected("not a url");
        assertRejected("");
        assertRejected("   ");
        assertRejected(null);
        assertRejected("jdbc:mysql://localhost:not-a-port/student_portal");
        assertRejected("jdbc:mysql://localhost:3306/student_portal?x=%zz");
    }

    @Test
    void rejectsWrongProtocol() {
        assertRejected("jdbc:postgresql://localhost:3306/student_portal");
        assertRejected("mysql://localhost:3306/student_portal");
        assertRejected("http://localhost:3306/student_portal");
    }

    @Test
    void rejectsMissingHost() {
        assertRejected("jdbc:mysql:///student_portal");
    }

    @Test
    void rejectsMissingSchema() {
        assertRejected("jdbc:mysql://localhost:3306/");
        assertRejected("jdbc:mysql://localhost:3306");
    }

    @Test
    void rejectsNestedSchemaPath() {
        assertRejected("jdbc:mysql://localhost:3306/other/student_portal");
    }

    @Test
    void rejectsInvalidPort() {
        assertRejected("jdbc:mysql://localhost:70000/student_portal");
        assertRejected("jdbc:mysql://localhost:0/student_portal");
    }

    @Test
    void diagnosticNeverContainsPasswordOrSensitiveQueryParameters() {
        String url = "jdbc:mysql://localhost:3306/student_portal_test?password=TopSecret123&user=admin";
        assertThatThrownBy(() -> DatabaseTargetValidator.validate(url))
            .isInstanceOf(IllegalArgumentException.class)
            .satisfies(exception -> {
                String message = exception.getMessage();
                assertThat(message).doesNotContain("TopSecret123");
                assertThat(message).doesNotContain("student_portal_test?");
                assertThat(message).doesNotContain(url);
            });
    }

    @Test
    void redactsNonIdentifierSchemaInDiagnostic() {
        String url = "jdbc:mysql://localhost:3306/not%20a%20schema%2Fsecret";
        assertThatThrownBy(() -> DatabaseTargetValidator.validate(url))
            .isInstanceOf(IllegalArgumentException.class)
            .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("secret"));
    }
}
