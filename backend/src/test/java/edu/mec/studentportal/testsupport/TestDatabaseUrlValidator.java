package edu.mec.studentportal.testsupport;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Validates that a MySQL JDBC connection URL targets exactly the
 * {@value #REQUIRED_SCHEMA} test schema before any connection attempt or Spring
 * DataSource configuration.
 *
 * <p>Exception messages never echo the raw URL, its query string, or any credentials.
 */
public final class TestDatabaseUrlValidator {
    /** The only schema integration tests may target. */
    public static final String REQUIRED_SCHEMA = "student_portal_test";

    public static final String REQUIRED_SCHEMA_EXAMPLE_URL =
        "jdbc:mysql://localhost:3306/" + REQUIRED_SCHEMA + "?serverTimezone=Asia/Kolkata";

    private static final String JDBC_MYSQL_PREFIX = "jdbc:mysql://";
    private static final String JDBC_MYSQL_PREFIX_MASK = "jdbc:mysql:";

    private TestDatabaseUrlValidator() {}

    /**
     * Validates the URL and returns it unchanged when safe.
     *
     * @throws IllegalStateException if the URL is unsafe, malformed, or targets the wrong schema
     */
    public static String validate(String url) {
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
        return trimmed;
    }

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
}
