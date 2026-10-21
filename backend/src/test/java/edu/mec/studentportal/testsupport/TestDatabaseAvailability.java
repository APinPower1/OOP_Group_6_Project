package edu.mec.studentportal.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Connectivity probe for the MySQL test database used by DB-backed module tests. Tests are
 * skipped (not failed) when the database is unreachable so a plain {@code ./gradlew build}
 * stays green on machines without MySQL.
 *
 * <p>URLs are validated by {@link TestDatabaseUrlValidator} before any connection attempt:
 * only the exact {@code student_portal_test} schema is allowed; unsafe or malformed
 * configuration throws {@link IllegalStateException} rather than being skipped.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code TEST_DB_URL} — default {@code jdbc:mysql://localhost:3306/student_portal_test?serverTimezone=Asia/Kolkata}</li>
 *   <li>{@code TEST_DB_USERNAME} — default {@code student_portal_test_app}</li>
 *   <li>{@code TEST_DB_PASSWORD} — no default; tests skip when unset/unreachable</li>
 * </ul>
 */
public final class TestDatabaseAvailability {
    static final String DEFAULT_URL = TestDatabaseUrlValidator.REQUIRED_SCHEMA_EXAMPLE_URL;
    static final String DEFAULT_USERNAME = "student_portal_test_app";

    private static Boolean available;

    private TestDatabaseAvailability() {}

    /** JUnit {@code @EnabledIf} entry point. */
    public static synchronized boolean isAvailable() {
        String url = envOrDefault("TEST_DB_URL", DEFAULT_URL);
        TestDatabaseUrlValidator.validate(url);
        if (available != null) {
            return available;
        }
        String username = envOrDefault("TEST_DB_USERNAME", DEFAULT_USERNAME);
        String password = System.getenv("TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            System.err.println("Skipping DB-backed tests: TEST_DB_PASSWORD is not set.");
            available = false;
            return false;
        }
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            available = connection.isValid(3);
        } catch (Exception exception) {
            System.err.println("Skipping DB-backed tests: cannot connect to the required test schema '"
    + TestDatabaseUrlValidator.REQUIRED_SCHEMA + "'.");
            available = false;
        }
        return available;
    }

    public static String url() {
        String url = envOrDefault("TEST_DB_URL", DEFAULT_URL);
        TestDatabaseUrlValidator.validate(url);
        return url;
    }

    public static String username() {
        return envOrDefault("TEST_DB_USERNAME", DEFAULT_USERNAME);
    }

    public static String password() {
        String password = System.getenv("TEST_DB_PASSWORD");
        return password == null ? "" : password;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
