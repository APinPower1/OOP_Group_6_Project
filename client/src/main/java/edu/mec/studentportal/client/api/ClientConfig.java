package edu.mec.studentportal.client.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Client connection settings. Loading order (first found wins): system property
 * {@code client.config} pointing at a properties file, {@code client-config.properties} in the
 * working directory, {@code client/client-config.properties}, then built-in defaults matching
 * {@code client/client-config.example.properties}. No database settings exist here.
 */
public record ClientConfig(String baseUrl, int connectTimeoutMillis, int requestTimeoutSeconds) {
    public static final String DEFAULT_BASE_URL = "http://localhost:8080/api";
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 15;

    public ClientConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        String normalized = baseUrl.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("baseUrl must start with http:// or https://");
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.toLowerCase(Locale.ROOT).endsWith("/api")) {
            baseUrl = normalized;
        } else {
            baseUrl = normalized + "/api";
        }
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException("connectTimeoutMillis must be positive");
        }
        if (requestTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("requestTimeoutSeconds must be positive");
        }
    }

    public static ClientConfig load() {
        Properties properties = new Properties();
        String explicit = System.getProperty("client.config");
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit.trim());
            if (Files.isRegularFile(path)) {
                readInto(properties, path);
                return from(properties);
            }
        }
        for (String candidate : new String[] {
            "client-config.properties",
            Path.of("client", "client-config.properties").toString()
        }) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                readInto(properties, path);
                return from(properties);
            }
        }
        return from(properties);
    }

    public static ClientConfig from(Properties properties) {
        String baseUrl = properties.getProperty("api.baseUrl", DEFAULT_BASE_URL);
        int connect = intProperty(
            properties,
            "api.connectTimeoutMillis",
            DEFAULT_CONNECT_TIMEOUT_MILLIS
        );
        int request = intProperty(
            properties,
            "api.requestTimeoutSeconds",
            DEFAULT_REQUEST_TIMEOUT_SECONDS
        );
        return new ClientConfig(baseUrl, connect, request);
    }

    private static void readInto(Properties properties, Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read client config at " + path, exception);
        }
    }

    private static int intProperty(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
