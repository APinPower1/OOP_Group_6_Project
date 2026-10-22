package edu.mec.studentportal.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for cross-module acceptance tests that exercise the REAL security chain:
 * {@code JwtAuthenticationFilter} + {@code BootstrapSecurityConfig} against real MySQL.
 * Unlike {@code DatabaseIntegrationTestSupport}, no test auth filter is imported — tokens
 * come from {@code POST /api/auth/login} and role enforcement is the production policy.
 * Concrete test classes must carry {@code @EnabledIf(TestDatabaseAvailability#isAvailable)}
 * since conditions are not inherited.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AcceptanceTestSupport {
    protected static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");
    protected static final String PASSWORD = "Acceptance-Pass-123";
    private static final DateTimeFormatter API_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    private final AtomicLong uniqueIds = new AtomicLong(System.nanoTime());

    @DynamicPropertySource
    static void testDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabaseUrlHolder::url);
        registry.add("spring.datasource.username", TestDatabaseUrlHolder::username);
        registry.add("spring.datasource.password", TestDatabaseUrlHolder::password);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.jwt.secret", () -> "student-portal-local-test-secret-0123456789");
    }

    /** Defers to testsupport so URL validation and env defaults stay in one place. */
    static final class TestDatabaseUrlHolder {
        private TestDatabaseUrlHolder() {}

        static String url() {
            return edu.mec.studentportal.testsupport.TestDatabaseAvailability.url();
        }

        static String username() {
            return edu.mec.studentportal.testsupport.TestDatabaseAvailability.username();
        }

        static String password() {
            return edu.mec.studentportal.testsupport.TestDatabaseAvailability.password();
        }
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM feedback");
        jdbcTemplate.update("DELETE FROM announcements");
        jdbcTemplate.update("DELETE FROM club_members");
        jdbcTemplate.update("DELETE FROM clubs");
        jdbcTemplate.update("DELETE FROM registrations");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM student_profiles");
        jdbcTemplate.update("DELETE FROM organizer_profiles");
        jdbcTemplate.update("DELETE FROM faculty_profiles");
        jdbcTemplate.update("DELETE FROM admin_profiles");
        jdbcTemplate.update("DELETE FROM users");
    }

    protected LocalDateTime now() {
        return LocalDateTime.now(CAMPUS_ZONE).withNano(0);
    }

    protected long nextId() {
        return uniqueIds.incrementAndGet();
    }

    protected String emailFor(long userId) {
        return "accept-" + userId + "@test.example.edu";
    }

    protected long createUser(String role) {
        return createUser(role, true);
    }

    /** Inserts a fully profiled user with BCrypt-hashed {@link #PASSWORD}. */
    protected long createUser(String role, boolean enabled) {
        long id = uniqueIds.incrementAndGet();
        jdbcTemplate.update(
            "INSERT INTO users (id, full_name, email, password_hash, department, role, enabled) "
                + "VALUES (?, ?, ?, ?, 'CSE', ?, ?)",
            id,
            "Accept " + role + " " + id,
            emailFor(id),
            passwordEncoder.encode(PASSWORD),
            role,
            enabled
        );
        switch (role) {
            case "STUDENT" -> jdbcTemplate.update(
                "INSERT INTO student_profiles (user_id, roll_number, semester, batch) "
                    + "VALUES (?, ?, 3, '2026')",
                id, "ACC-R-" + id
            );
            case "ORGANIZER" -> jdbcTemplate.update(
                "INSERT INTO organizer_profiles (user_id, designation) VALUES (?, 'Event Coordinator')",
                id
            );
            case "FACULTY" -> jdbcTemplate.update(
                "INSERT INTO faculty_profiles (user_id, employee_id, designation) "
                    + "VALUES (?, ?, 'Professor')",
                id, "ACC-E-" + id
            );
            case "ADMIN" -> jdbcTemplate.update(
                "INSERT INTO admin_profiles (user_id, admin_level) VALUES (?, 1)",
                id
            );
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        }
        return id;
    }

    /** Performs a real login and returns the raw JWT; fails the test when login is refused. */
    protected String bearerToken(String email, String password) {
        try {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            String token = body.path("accessToken").asText();
            assertTrue(!token.isBlank(), "login must return an access token");
            return token;
        } catch (Exception exception) {
            throw new IllegalStateException("login failed for " + email, exception);
        }
    }

    protected String bearerFor(long userId) {
        return "Bearer " + bearerToken(emailFor(userId), PASSWORD);
    }

    protected JsonNode getJson(String bearer, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Creates a proposal through the real API and returns the parsed event. */
    protected JsonNode createEventViaApi(
        String organizerBearer,
        String title,
        String venue,
        LocalDateTime start,
        LocalDateTime end,
        int capacity
    ) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
            "title", title,
            "description", "Acceptance scenario event.",
            "venue", venue,
            "startTime", start.format(API_TIME),
            "endTime", end.format(API_TIME),
            "capacity", capacity
        ));
        MvcResult result = mockMvc.perform(post("/api/events")
                .header(HttpHeaders.AUTHORIZATION, organizerBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Full real-chain flow: organizer proposal + faculty approval; returns the event id. */
    protected long createApprovedEvent(
        long organizerId,
        long facultyId,
        String venue,
        LocalDateTime start,
        int capacity
    ) throws Exception {
        JsonNode event = createEventViaApi(
            bearerFor(organizerId),
            "Acceptance Event " + nextId(),
            venue,
            start,
            start.plusHours(2),
            capacity
        );
        long eventId = event.path("id").asLong();
        mockMvc.perform(post("/api/events/" + eventId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(facultyId)))
            .andExpect(status().isOk());
        return eventId;
    }

    protected JsonNode eventById(long eventId) throws Exception {
        return getJson(bearerFor(createFacultyReader()), "/api/events/" + eventId);
    }

    private long facultyReader;

    /**
     * Lazily creates one faculty account used only for privileged reads in helpers;
     * callers that care about a specific reviewer create their own users instead.
     */
    private long createFacultyReader() {
        if (facultyReader == 0) {
            facultyReader = createUser("FACULTY");
        }
        return facultyReader;
    }

    protected int availableSlots(long eventId) throws Exception {
        JsonNode event = eventById(eventId);
        assertEquals(eventId, event.path("id").asLong());
        return event.path("availableSlots").asInt();
    }
}
