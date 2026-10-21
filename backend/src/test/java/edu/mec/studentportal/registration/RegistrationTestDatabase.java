package edu.mec.studentportal.registration;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;

/**
 * Connectivity probe and URL safety guard for the MySQL test database used by registration
 * integration/concurrency tests. Tests are skipped (not failed) when the correctly configured
 * database is unreachable or {@code TEST_DB_PASSWORD} is absent, so a plain
 * {@code ./gradlew build} stays green on machines without MySQL.
 *
 * <p>Safety guard: the configured {@code TEST_DB_URL} must target the exact
 * {@value #REQUIRED_SCHEMA} schema. A URL for the production {@code student_portal} database,
 * any other schema, embedded userinfo, a missing host/schema, or a malformed URL fails loudly
 * with {@link IllegalStateException} instead of being skipped — integration-test cleanup runs
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
    static final String REQUIRED_SCHEMA = "student_portal_test";

    static final String REQUIRED_SCHEMA_EXAMPLE_URL =
        "jdbc:mysql://localhost:3306/" + REQUIRED_SCHEMA + "?serverTimezone=Asia/Kolkata";

    static final String DEFAULT_URL = REQUIRED_SCHEMA_EXAMPLE_URL;
    static final String DEFAULT_USERNAME = "student_portal_test_app";

    private static final String JDBC_MYSQL_PREFIX = "jdbc:mysql://";
    private static final String JDBC_MYSQL_PREFIX_MASK = "jdbc:mysql:";

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

    /**
     * Verifies the JDBC URL targets exactly {@value #REQUIRED_SCHEMA} via {@link URI} parsing.
     * Rejects wrong or missing schemas (including production {@code student_portal}), wrong
     * protocols, missing hosts, embedded userinfo, out-of-range ports, and malformed URLs.
     * The exception message never contains the raw URL, query parameters, or credentials.
     */
    static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw unsafeUrl("the URL is blank.");
        }
        String trimmed = url.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(JDBC_MYSQL_PREFIX)) {
            throw unsafeUrl("the protocol must be " + JDBC_MYSQL_PREFIX);
        }
        URI uri;
        try {
            uri = new URI(trimmed.substring(JDBC_MYSQL_PREFIX_MASK.length()));
        } catch (URISyntaxException exception) {
            throw unsafeUrl("the URL is not a well-formed URI.");
        }
        if (uri.getRawUserInfo() != null) {
            throw unsafeUrl(
                "embedded userinfo is not allowed; supply the password via TEST_DB_PASSWORD."
            );
        }
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isBlank()) {
            throw unsafeUrl("the URL must include a host (and optional port).");
        }
        // getHost() is non-null only for a server-based authority with a well-formed
        // host and numeric port; registry-based forms like "localhost:not-a-port"
        // parse with host=null and must be refused.
        if (uri.getHost() == null) {
            throw unsafeUrl("the authority must be a well-formed host with an optional numeric port.");
        }
        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            throw unsafeUrl("the port must be between 1 and 65535.");
        }
        String path = uri.getRawPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            throw unsafeUrl("the URL must include the database schema in the path.");
        }
        String schema = path.startsWith("/") ? path.substring(1) : path;
        if (schema.contains("/")) {
            throw unsafeUrl("the URL path must contain exactly one schema segment.");
        }
        if (!REQUIRED_SCHEMA.equals(schema)) {
            throw unsafeUrl("the schema must be exactly '" + REQUIRED_SCHEMA
                + "' but the URL targets " + printableSchema(schema) + ".");
        }
    }

    /**
     * Echoes a rejected schema only when it is a plain identifier; anything else (which could
     * smuggle a secret into a log line) is replaced with a placeholder.
     */
    private static String printableSchema(String schema) {
        return schema.matches("[A-Za-z0-9_]{1,64}") ? "'" + schema + "'" : "<redacted>";
    }

    private static IllegalStateException unsafeUrl(String reason) {
        return new IllegalStateException(
            "Unsafe or misconfigured TEST_DB_URL: " + reason
                + " Integration tests require the exact schema '" + REQUIRED_SCHEMA + "'."
                + " Safe example: " + REQUIRED_SCHEMA_EXAMPLE_URL
                + " Refusing to run so test cleanup cannot modify the wrong database."
        );
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
