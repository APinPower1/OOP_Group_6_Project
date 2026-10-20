package edu.mec.studentportal.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Connectivity probe for the MySQL test database used by DB-backed module tests. Tests are
 * skipped (not failed) when the database is unreachable so a plain {@code ./gradlew build}
 * stays green on machines without MySQL.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code TEST_DB_URL} — default {@code jdbc:mysql://localhost:3306/student_portal_test?serverTimezone=Asia/Kolkata}</li>
 *   <li>{@code TEST_DB_USERNAME} — default {@code student_portal_app}</li>
 *   <li>{@code TEST_DB_PASSWORD} — no default; tests skip when unset/unreachable</li>
 * </ul>
 */
public final class TestDatabaseAvailability {
    static final String DEFAULT_URL =
        "jdbc:mysql://localhost:3306/student_portal_test?serverTimezone=Asia/Kolkata";
    static final String DEFAULT_USERNAME = "student_portal_app";

    private static Boolean available;

    private TestDatabaseAvailability() {}

    /** JUnit {@code @EnabledIf} entry point. */
    public static synchronized boolean isAvailable() {
        if (available != null) {
            return available;
        }
        String url = envOrDefault("TEST_DB_URL", DEFAULT_URL);
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
            System.err.println("Skipping DB-backed tests: cannot reach test database at "
                + url + " (" + exception.getMessage() + ")");
            available = false;
        }
        return available;
    }

    public static String url() {
        return envOrDefault("TEST_DB_URL", DEFAULT_URL);
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
