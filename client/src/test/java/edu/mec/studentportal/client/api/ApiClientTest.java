package edu.mec.studentportal.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiClientTest {
    private static final String BASE_URL_PROPERTY = "studentportal.api.baseUrl";

    private HttpServer server;
    private String previousBaseUrl;

    @BeforeEach
    void startServer() throws IOException {
        previousBaseUrl = System.getProperty(BASE_URL_PROPERTY);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        System.setProperty(
            BASE_URL_PROPERTY,
            "http://localhost:" + server.getAddress().getPort() + "/api"
        );
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        if (previousBaseUrl == null) {
            System.clearProperty(BASE_URL_PROPERTY);
        } else {
            System.setProperty(BASE_URL_PROPERTY, previousBaseUrl);
        }
    }

    @Test
    void sendsBearerTokenAndParsesJsonResponse() {
        server.createContext("/api/events", exchange -> {
            assertEquals("Bearer test-token", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "[{\"id\":7}]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        ApiClient client = new ApiClient(new ObjectMapper(), () -> "test-token");

        assertEquals(7, client.get("events").get(0).path("id").asInt());
    }

    @Test
    void exposesStandardApiErrorCodeAndMessage() {
        server.createContext("/api/events", exchange -> {
            byte[] response = """
                {"status":403,"code":"FORBIDDEN","message":"Access denied."}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(403, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        ApiClient client = new ApiClient(new ObjectMapper(), () -> null);

        ApiException exception = assertThrows(ApiException.class, () -> client.get("events"));

        assertEquals(403, exception.status());
        assertEquals("FORBIDDEN", exception.code());
        assertEquals("Access denied.", exception.getMessage());
    }

    @Test
    void serializesRequestBodiesAsJson() throws IOException {
        server.createContext("/api/auth/login", exchange -> {
            assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("demo@example.edu", new ObjectMapper().readTree(body).path("email").asText());
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        ApiClient client = new ApiClient(new ObjectMapper(), () -> null);

        client.post("auth/login", Map.of("email", "demo@example.edu", "password", "demo-password"));
    }
}
