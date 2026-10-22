package edu.mec.studentportal.client.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.mec.studentportal.client.dto.ApiErrorBody;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * JDK {@code java.net.http.HttpClient} implementation of {@link ApiClient}. Centralizes JSON,
 * bearer-token injection, timeouts, 401 session clearing, 403 propagation and error-body
 * mapping into {@link ApiException}. Runs entirely off the Swing EDT (callers use ApiTask).
 */
public final class HttpApiClient implements ApiClient {
    private final ClientConfig config;
    private final SessionContext session;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public HttpApiClient(ClientConfig config, SessionContext session) {
        this(config, session, defaultMapper());
    }

    HttpApiClient(ClientConfig config, SessionContext session, ObjectMapper mapper) {
        this.config = config;
        this.session = session;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.connectTimeoutMillis()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    static ObjectMapper defaultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    @Override
    public <T> T get(String path, Class<T> responseType) {
        return exchange("GET", path, null, responseType);
    }

    @Override
    public <T> T get(String path, TypeReference<T> responseType) {
        return exchange("GET", path, null, responseType);
    }

    @Override
    public <T> T post(String path, Object requestBody, Class<T> responseType) {
        return exchange("POST", path, requestBody, responseType);
    }

    @Override
    public <T> T post(String path, Object requestBody, TypeReference<T> responseType) {
        return exchange("POST", path, requestBody, responseType);
    }

    @Override
    public <T> T put(String path, Object requestBody, Class<T> responseType) {
        return exchange("PUT", path, requestBody, responseType);
    }

    @Override
    public <T> T patch(String path, Object requestBody, Class<T> responseType) {
        return exchange("PATCH", path, requestBody, responseType);
    }

    @Override
    public void delete(String path) {
        exchange("DELETE", path, null, Void.class);
    }

    @Override
    public <T> T delete(String path, Class<T> responseType) {
        return exchange("DELETE", path, null, responseType);
    }

    private <T> T exchange(String method, String path, Object requestBody, Object responseType) {
        String url = config.baseUrl() + normalizePath(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
            .header("Accept", "application/json");
        String token = session.getToken();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (requestBody != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(writeJson(requestBody)));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new ApiException(
                ApiException.NETWORK_STATUS,
                ApiException.NETWORK_ERROR,
                "Cannot reach the server at " + config.baseUrl() + "."
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                ApiException.NETWORK_STATUS,
                ApiException.NETWORK_ERROR,
                "The request was interrupted."
            );
        }

        int status = response.statusCode();
        String payload = response.body();
        if (status >= 200 && status < 300) {
            if (responseType == Void.class || payload == null || payload.isBlank()) {
                return null;
            }
            try {
                @SuppressWarnings("unchecked")
                T parsed = (T) mapper.readValue(payload, javaType(responseType));
                return parsed;
            } catch (JsonProcessingException exception) {
                throw new ApiException(
                    status,
                    "INTERNAL_ERROR",
                    "The server returned a response this client could not read."
                );
            }
        }

        ApiErrorBody error = parseError(payload);
        if (status == 401) {
            session.clearSession();
        }
        if (error != null && error.code() != null && !error.code().isBlank()) {
            String message = error.message() != null && !error.message().isBlank()
                ? error.message()
                : defaultMessage(status);
            throw new ApiException(status, error.code(), message);
        }
        throw new ApiException(status, genericCode(status), defaultMessage(status));
    }

    private JavaType javaType(Object responseType) {
        if (responseType instanceof Class<?> type) {
            return mapper.getTypeFactory().constructType(type);
        }
        @SuppressWarnings("unchecked")
        TypeReference<?> reference = (TypeReference<?>) responseType;
        return mapper.getTypeFactory().constructType(reference);
    }

    private String writeJson(Object requestBody) {
        try {
            return mapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Request body could not be serialized", exception);
        }
    }

    private ApiErrorBody parseError(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(payload, ApiErrorBody.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String genericCode(int status) {
        return switch (status) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status >= 500 ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
    }

    private static String defaultMessage(int status) {
        return switch (status) {
            case 401 -> "Your session has ended. Please sign in again.";
            case 403 -> "You do not have access to this action.";
            case 404 -> "The requested record was not found.";
            case 409 -> "The request conflicts with the current state.";
            default -> "Request failed (HTTP " + status + ").";
        };
    }
}
