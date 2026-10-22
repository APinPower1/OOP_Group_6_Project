package edu.mec.studentportal.registration;

import edu.mec.studentportal.testsupport.TestDatabaseUrlValidator;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Connectivity probe and URL safety guard for the MySQL test database used by registration
 * integration/concurrency tests. Tests are skipped (not failed) when the correctly configured
 * database is unreachable or {@code TEST_DB_PASSWORD} is absent, so a plain
 * {@code ./gradlew build} stays green on machines without MySQL.
 *
 * <p>Safety guard: the configured {@code TEST_DB_URL} must target the exact
 * {@value TestDatabaseUrlValidator#REQUIRED_SCHEMA} schema. Delegates to
 * {@link TestDatabaseUrlValidator} to reuse shared validation logic.
 * A URL for the production {@code student_portal} database, any other schema, embedded
 * userinfo, a missing host/schema, or a malformed URL fails loudly with
 * {@link IllegalStateException} instead of being skipped — integration-test cleanup runs
 * {@code DELETE} statements and must never touch the wrong database. Diagnostics never echo
 * the raw URL, its query string, or any credentials.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code TEST_DB_URL} — default {@code jdbc:mysql://localhost:3306/student_portal_test?serverTimezone=Asia/Kolkata}</li>
 *   <li>{@code TEST_DB_USERNAME} — default {@code student_portal_test_app} (privileges scoped to {@code student_portal_test.*})</li>
 *   <li>{@code TEST_DB_PASSWORD} — no default; tests skip when unset/unreachable</li>
 * </ul>
 *
 * <p>Example with the documented local MySQL 8.4 setup (database {@code student_portal_test}
 * created empty; Flyway applies the schema at context startup):
 * {@code TEST_DB_PASSWORD=... ./gradlew :backend:test}
 */
public final class RegistrationTestDatabase {
    /** The only schema integration tests may target; anything else is refused. */
    static final String REQUIRED_SCHEMA = TestDatabaseUrlValidator.REQUIRED_SCHEMA;

    static final String REQUIRED_SCHEMA_EXAMPLE_URL = TestDatabaseUrlValidator.REQUIRED_SCHEMA_EXAMPLE_URL;

    static final String DEFAULT_URL = TestDatabaseUrlValidator.REQUIRED_SCHEMA_EXAMPLE_URL;
    static final String DEFAULT_USERNAME = "student_portal_test_app";

    private static Boolean available;

    private RegistrationTestDatabase() {}

    /**
     * JUnit {@code @EnabledIf} entry point. Validates the URL first: an unsafe or malformed
     * URL throws instead of being treated as "unavailable", so misconfiguration fails the
     * build rather than silently skipping every database test.
     */
    public static synchronized boolean isAvailable() {
        String url = envOrDefault("TEST_DB_URL", DEFAULT_URL);
        validateUrl(url);
        if (available != null) {
            return available;
        }
        String username = envOrDefault("TEST_DB_USERNAME", DEFAULT_USERNAME);
        String password = System.getenv("TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            System.err.println("Skipping registration DB tests: TEST_DB_PASSWORD is not set.");
            available = false;
            return false;
        }
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            available = connection.isValid(3);
        } catch (Exception exception) {
            System.err.println("Skipping registration DB tests: cannot reach the required test schema '"
                + REQUIRED_SCHEMA + "' (" + exception.getMessage() + ")");
            available = false;
        }
        return available;
    }

    /** Validated test JDBC URL; safe to hand to Spring's test DataSource. */
    public static String url() {
        String url = envOrDefault("TEST_DB_URL", DEFAULT_URL);
        validateUrl(url);
        return url;
    }

    public static String username() {
        return envOrDefault("TEST_DB_USERNAME", DEFAULT_USERNAME);
    }

    public static String password() {
        String password = System.getenv("TEST_DB_PASSWORD");
        return password == null ? "" : password;
    }

    /** Delegates to {@link TestDatabaseUrlValidator} to reuse shared validation logic. */
    static void validateUrl(String url) {
        TestDatabaseUrlValidator.validate(url);
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
