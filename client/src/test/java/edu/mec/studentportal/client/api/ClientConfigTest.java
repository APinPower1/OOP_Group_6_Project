package edu.mec.studentportal.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientConfigTest {

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("client.config");
    }

    @Test
    void appendsApiSuffixAndStripsTrailingSlashes() {
        assertEquals("http://localhost:8080/api",
            new ClientConfig("http://localhost:8080", 5000, 15).baseUrl());
        assertEquals("http://localhost:8080/api",
            new ClientConfig("http://localhost:8080///", 5000, 15).baseUrl());
        assertEquals("https://portal.mec.edu/api",
            new ClientConfig("https://portal.mec.edu", 5000, 15).baseUrl());
    }

    @Test
    void keepsBaseUrlThatAlreadyEndsWithApi() {
        assertEquals("http://localhost:8080/api",
            new ClientConfig("http://localhost:8080/api", 5000, 15).baseUrl());
        assertEquals("http://localhost:8080/api",
            new ClientConfig("http://localhost:8080/api/", 5000, 15).baseUrl());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
            () -> new ClientConfig("  ", 5000, 15));
        assertThrows(IllegalArgumentException.class,
            () -> new ClientConfig("ftp://localhost", 5000, 15));
        assertThrows(IllegalArgumentException.class,
            () -> new ClientConfig("http://localhost", 0, 15));
        assertThrows(IllegalArgumentException.class,
            () -> new ClientConfig("http://localhost", 5000, -1));
    }

    @Test
    void usesDefaultsWhenPropertiesAreEmpty() {
        ClientConfig config = ClientConfig.from(new Properties());
        assertEquals(ClientConfig.DEFAULT_BASE_URL, config.baseUrl());
        assertEquals(ClientConfig.DEFAULT_CONNECT_TIMEOUT_MILLIS, config.connectTimeoutMillis());
        assertEquals(ClientConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS, config.requestTimeoutSeconds());
    }

    @Test
    void readsPropertiesAndFallsBackOnBadNumbers() {
        Properties properties = new Properties();
        properties.setProperty("api.baseUrl", "https://example.edu");
        properties.setProperty("api.connectTimeoutMillis", "2500");
        properties.setProperty("api.requestTimeoutSeconds", "not-a-number");
        ClientConfig config = ClientConfig.from(properties);
        assertEquals("https://example.edu/api", config.baseUrl());
        assertEquals(2500, config.connectTimeoutMillis());
        assertEquals(ClientConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS,
            config.requestTimeoutSeconds());
    }

    @Test
    void loadsFromExplicitSystemPropertyFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("custom.properties");
        Files.writeString(file, """
            api.baseUrl=http://192.168.1.10:9090
            api.connectTimeoutMillis=1500
            api.requestTimeoutSeconds=7
            """);
        System.setProperty("client.config", file.toString());

        ClientConfig config = ClientConfig.load();
        assertEquals("http://192.168.1.10:9090/api", config.baseUrl());
        assertEquals(1500, config.connectTimeoutMillis());
        assertEquals(7, config.requestTimeoutSeconds());
    }

    @Test
    void fallsBackToDefaultsWhenExplicitFileMissing(@TempDir Path tempDir) {
        System.setProperty("client.config",
            tempDir.resolve("does-not-exist.properties").toString());
        ClientConfig config = ClientConfig.load();
        assertEquals(ClientConfig.DEFAULT_BASE_URL, config.baseUrl());
        assertEquals(ClientConfig.DEFAULT_CONNECT_TIMEOUT_MILLIS,
            config.connectTimeoutMillis());
        assertEquals(ClientConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS,
            config.requestTimeoutSeconds());
    }
}
