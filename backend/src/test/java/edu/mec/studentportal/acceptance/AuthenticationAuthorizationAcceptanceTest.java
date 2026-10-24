package edu.mec.studentportal.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance scenario 1 (MEMBER-09): all four roles authenticate through the real login
 * endpoint; unauthorized callers get 401/403 from the production security chain, not a test
 * filter; disabled accounts lose access for future requests.
 */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class AuthenticationAuthorizationAcceptanceTest extends AcceptanceTestSupport {

    @Test
    void allFourRolesAuthenticateAndReceiveRoleSpecificTokens() throws Exception {
        for (String role : List.of("STUDENT", "ORGANIZER", "FACULTY", "ADMIN")) {
            long id = createUser(role);
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"%s","password":"%s"}""".formatted(emailFor(id), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.id").value(id))
                .andExpect(jsonPath("$.user.role").value(role))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }
    }

    @Test
    void wrongPasswordReturnsGenericUnauthorizedWithoutAccountEnumeration() throws Exception {
        long id = createUser("STUDENT");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"definitely-wrong"}""".formatted(emailFor(id))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"nobody-at-all@test.example.edu","password":"definitely-wrong"}"""))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void anonymousCallToProtectedRouteIsUnauthenticated() throws Exception {
        createUser("STUDENT");
        mockMvc.perform(get("/api/events"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void studentAndOrganizerAreForbiddenFromAdminRoutes() throws Exception {
        long studentId = createUser("STUDENT");
        long organizerId = createUser("ORGANIZER");

        mockMvc.perform(get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(organizerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Nope","email":"nope@test.example.edu","department":"CSE",
                     "role":"STUDENT","password":"Initial-Pass-123",
                     "profile":{"rollNumber":"R-NOPE","semester":1,"batch":"2026"}}"""))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void onlyOrganizersMayCreateEvents() throws Exception {
        for (String role : List.of("STUDENT", "FACULTY", "ADMIN")) {
            long id = createUser(role);
            mockMvc.perform(post("/api/events")
                    .header(HttpHeaders.AUTHORIZATION, bearerFor(id))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"title":"Should Fail","description":"x","venue":"Hall Z",
                         "startTime":"2027-03-01T10:00:00","endTime":"2027-03-01T12:00:00",
                         "capacity":10}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void studentCannotPublishAnnouncements() throws Exception {
        long studentId = createUser("STUDENT");
        mockMvc.perform(post("/api/announcements")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Nope","content":"x","audienceRole":null}"""))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void disabledUserCannotLoginAndExistingTokenStopsWorking() throws Exception {
        long id = createUser("STUDENT");
        String token = bearerFor(id);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", id);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s"}""".formatted(emailFor(id), PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void authMeEchoesSignedInUserSummary() throws Exception {
        long id = createUser("ORGANIZER");
        JsonNode me = getJson(bearerFor(id), "/api/auth/me");
        String raw = me.toString();
        assertFalse(raw.contains("password"), "auth/me must never echo credential material");
        assertTrue(raw.contains("ORGANIZER"), "role must be present: " + raw);
        assertEquals(emailFor(id), firstText(me, "email"));
    }

    private static String firstText(JsonNode node, String field) {
        if (node.hasNonNull(field)) {
            return node.get(field).asText();
        }
        JsonNode user = node.get("user");
        if (user != null && user.hasNonNull(field)) {
            return user.get(field).asText();
        }
        throw new AssertionError("field " + field + " missing in " + node);
    }
}
