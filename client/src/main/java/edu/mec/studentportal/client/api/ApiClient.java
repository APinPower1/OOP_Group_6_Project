package edu.mec.studentportal.client.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.Objects;
import java.util.function.Supplier;

public final class ApiClient {
    private final ObjectMapper objectMapper;
    private final Supplier<String> tokenSupplier;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration requestTimeout;

    public ApiClient(ObjectMapper objectMapper, Supplier<String> tokenSupplier) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier);
        Properties configuration = loadConfiguration();
        String configuredBaseUrl = System.getProperty(
            "studentportal.api.baseUrl",
            System.getenv().getOrDefault(
                "STUDENT_PORTAL_API_URL",
                configuration.getProperty("api.baseUrl", "http://localhost:8080/api")
            )
        );
        URI configuredUri = URI.create(configuredBaseUrl);
        if (!"http".equalsIgnoreCase(configuredUri.getScheme())
            && !"https".equalsIgnoreCase(configuredUri.getScheme())) {
            throw new IllegalArgumentException("The API base URL must use HTTP or HTTPS.");
        }
        String normalizedBaseUrl = configuredBaseUrl.endsWith("/")
            ? configuredBaseUrl
            : configuredBaseUrl + "/";
        this.baseUri = URI.create(normalizedBaseUrl);
        long connectTimeoutMillis = Long.parseLong(System.getProperty(
            "studentportal.api.connectTimeoutMillis",
            configuration.getProperty("api.connectTimeoutMillis", "5000")
        ));
        long requestTimeoutSeconds = Long.parseLong(System.getProperty(
            "studentportal.api.requestTimeoutSeconds",
            configuration.getProperty("api.requestTimeoutSeconds", "15")
        ));
        if (connectTimeoutMillis < 1 || requestTimeoutSeconds < 1) {
            throw new IllegalArgumentException("API timeouts must be positive.");
        }
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
            .build();
    }

    public JsonNode get(String path) {
        return send("GET", path, null);
    }

    public JsonNode post(String path, Object body) {
        return send("POST", path, body);
    }

    public JsonNode put(String path, Object body) {
        return send("PUT", path, body);
    }

    public JsonNode patch(String path, Object body) {
        return send("PATCH", path, body);
    }

    public JsonNode delete(String path) {
        return send("DELETE", path, null);
    }

    private static Properties loadConfiguration() {
        Properties configuration = new Properties();
        Path configFile = Path.of("client-config.properties");
        if (!Files.isRegularFile(configFile)) {
            configFile = Path.of("client", "client-config.properties");
        }
        if (!Files.isRegularFile(configFile)) {
            configFile = Path.of("..", "client", "client-config.properties");
        }
        if (Files.isRegularFile(configFile)) {
            try (var input = Files.newInputStream(configFile)) {
                configuration.load(input);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read " + configFile + ".", exception);
            }
        }
        return configuration;
    }

    private JsonNode send(String method, String path, Object body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(requestTimeout)
            .header("Accept", "application/json");
        String token = tokenSupplier.get();
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            try {
                publisher = HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
            } catch (JsonProcessingException exception) {
                throw new ApiException(0, "REQUEST_SERIALIZATION_ERROR", "Unable to prepare the request.");
            }
            request.header("Content-Type", "application/json");
        }
        request.method(method, publisher);
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "REQUEST_INTERRUPTED", "The request was interrupted.");
        } catch (IOException exception) {
            throw new ApiException(
                0,
                "CONNECTION_ERROR",
                "Could not connect to the portal service. Check that the backend is running."
            );
        }
        JsonNode responseBody;
        try {
            responseBody = response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new ApiException(
                response.statusCode(),
                "INVALID_RESPONSE",
                "The portal service returned an unreadable response."
            );
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiError(response.statusCode(), responseBody);
        }
        return responseBody;
    }

    private ApiException apiError(int status, JsonNode body) {
        String code = text(body, "code", "HTTP_" + status);
        String message = text(body, "message", "The request could not be completed.");
        return new ApiException(status, code, message);
    }

    private static String text(JsonNode node, String name, String fallback) {
        JsonNode value = node.get(name);
        return value != null && value.isTextual() ? value.asText() : fallback;
    }
}
