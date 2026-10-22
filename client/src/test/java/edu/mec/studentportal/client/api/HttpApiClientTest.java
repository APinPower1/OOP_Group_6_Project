package edu.mec.studentportal.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.mec.studentportal.client.dto.LoginResponse;
import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.UserSummary;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HttpApiClient behaviour against a local fake server (no Spring, no real backend):
 * bearer injection, JSON mapping, 401 session clearing, 403 propagation, generic status
 * codes, network failures.
 */
class HttpApiClientTest {
    private HttpServer server;
    private SessionContext session;
    private HttpApiClient client;

    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        session = new SessionContext();
        client = new HttpApiClient(
            new ClientConfig("http://127.0.0.1:" + server.getAddress().getPort(),
                2000, 5),
            session);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastMethod.set(exchange.getRequestMethod());
        lastPath.set(exchange.getRequestURI().toString());
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8));
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, payload.length == 0 ? -1 : payload.length);
        if (payload.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        }
        exchange.close();
    }

    private void login() {
        session.setSession("jwt-token",
            new UserSummary(3, "Std One", "s@mec.edu", Role.STUDENT));
    }

    @Test
    void getParsesJsonAndSendsBearerToken() {
        login();
        responseStatus = 200;
        responseBody = """
            {"id":9,"fullName":"Faculty One","email":"f@mec.edu","role":"FACULTY"}""";

        UserSummary user = client.get("/auth/me", UserSummary.class);

        assertEquals(9, user.id());
        assertEquals("Faculty One", user.fullName());
        assertEquals(Role.FACULTY, user.role());
        assertEquals("GET", lastMethod.get());
        assertEquals("/api/auth/me", lastPath.get());
        assertEquals("Bearer jwt-token", lastAuthorization.get());
    }

    @Test
    void sendsNoAuthorizationHeaderBeforeLogin() {
        responseStatus = 200;
        responseBody = "[]";

        List<UserSummary> users = client.get("/admin/users", new TypeReference<>() {});

        assertTrue(users.isEmpty());
        assertNull(lastAuthorization.get());
    }

    @Test
    void normalizesPathsWithoutLeadingSlash() {
        responseStatus = 200;
        responseBody = "[]";

        client.get("events", new TypeReference<List<UserSummary>>() {});

        assertEquals("/api/events", lastPath.get());
    }

    @Test
    void postSerializesJsonBody() {
        responseStatus = 201;
        responseBody = "{\"accessToken\":\"t\",\"tokenType\":\"Bearer\","
            + "\"expiresInSeconds\":3600,"
            + "\"user\":{\"id\":1,\"fullName\":\"A\",\"email\":\"a@b\",\"role\":\"STUDENT\"}}";

        LoginResponse result = client.post("/auth/login",
            java.util.Map.of("email", "a@b", "password", "secret123"),
            LoginResponse.class);

        assertEquals("POST", lastMethod.get());
        assertEquals("application/json", lastContentType.get());
        assertTrue(lastBody.get().contains("\"email\":\"a@b\""));
        assertTrue(lastBody.get().contains("\"password\":\"secret123\""));
        assertNull(lastAuthorization.get());
        assertEquals("t", result.accessToken());
        assertEquals(Role.STUDENT, result.user().role());
    }

    @Test
    void emptySuccessBodyReturnsNull() {
        responseStatus = 204;
        responseBody = "";

        assertNull(client.delete("/clubs/5", Void.class));
        assertEquals("DELETE", lastMethod.get());
    }

    @Test
    void unauthorizedClearsSessionAndMapsErrorBody() {
        login();
        responseStatus = 401;
        responseBody = """
            {"timestamp":"2026-01-01T00:00:00Z","status":401,"code":"INVALID_CREDENTIALS",
             "message":"Bad credentials","path":"/api/auth/me"}""";

        ApiException exception = assertThrows(ApiException.class,
            () -> client.get("/auth/me", UserSummary.class));

        assertTrue(exception.isUnauthorized());
        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        assertEquals("Bad credentials", exception.getMessage());
        assertFalse(session.isLoggedIn(), "401 must clear the in-memory session");
    }

    @Test
    void forbiddenKeepsSessionAndUsesErrorBody() {
        login();
        responseStatus = 403;
        responseBody = """
            {"status":403,"code":"EVENT_NOT_YOURS","message":"Only the creator may edit."}""";

        ApiException exception = assertThrows(ApiException.class,
            () -> client.put("/events/1", java.util.Map.of(), UserSummary.class));

        assertTrue(exception.isForbidden());
        assertEquals("EVENT_NOT_YOURS", exception.getCode());
        assertEquals("Only the creator may edit.", exception.getMessage());
        assertTrue(session.isLoggedIn(), "403 must not log the user out");
    }

    @Test
    void statusWithoutBodyFallsBackToGenericCodeAndMessage() {
        responseStatus = 404;
        responseBody = "";

        ApiException exception = assertThrows(ApiException.class,
            () -> client.get("/events/999", UserSummary.class));

        assertEquals("NOT_FOUND", exception.getCode());
        assertEquals("The requested record was not found.", exception.getMessage());
    }

    @Test
    void serverErrorWithoutBodyMapsToInternalError() {
        responseStatus = 500;
        responseBody = "";

        ApiException exception = assertThrows(ApiException.class,
            () -> client.get("/health", UserSummary.class));

        assertEquals("INTERNAL_ERROR", exception.getCode());
        assertFalse(exception.isNetworkError());
    }

    @Test
    void unreachableServerYieldsNetworkError() {
        server.stop(0);
        server = null;

        ApiException exception = assertThrows(ApiException.class,
            () -> client.get("/health", UserSummary.class));

        assertTrue(exception.isNetworkError());
        assertEquals(ApiException.NETWORK_ERROR, exception.getCode());
        assertTrue(exception.getMessage().contains("Cannot reach the server"));
    }

    @Test
    void unparseableSuccessBodyMapsToInternalError() {
        responseStatus = 200;
        responseBody = "<html>not json</html>";

        ApiException exception = assertThrows(ApiException.class,
            () -> client.get("/auth/me", UserSummary.class));

        assertEquals(200, exception.getStatus());
        assertEquals("INTERNAL_ERROR", exception.getCode());
    }

    @Test
    void blankPathIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> client.get("  ", UserSummary.class));
    }

    @Test
    void errorBodyWithoutCodeUsesGenericMapping() {
        responseStatus = 409;
        responseBody = """
            {"status":409,"message":"Seat already taken."}""";

        ApiException exception = assertThrows(ApiException.class,
            () -> client.post("/events/1/register", java.util.Map.of(), UserSummary.class));

        assertEquals("CONFLICT", exception.getCode());
        assertEquals("The request conflicts with the current state.", exception.getMessage());
    }
}
