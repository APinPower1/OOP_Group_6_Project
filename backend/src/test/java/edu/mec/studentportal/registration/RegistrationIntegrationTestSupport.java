package edu.mec.studentportal.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared plumbing for registration integration/concurrency tests against a real MySQL
 * database (FOR UPDATE and unique-constraint semantics cannot be faked with an embedded DB).
 * Concrete classes carry {@code @EnabledIf} (conditions are not inherited from this base);
 * see {@link RegistrationTestDatabase}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RegistrationTestSecurityConfig.class)
abstract class RegistrationIntegrationTestSupport {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    MockMvc mockMvc;

    private final AtomicLong uniqueIds = new AtomicLong(System.nanoTime());

    @DynamicPropertySource
    static void registrationTestDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", RegistrationTestDatabase::url);
        registry.add("spring.datasource.username", RegistrationTestDatabase::username);
        registry.add("spring.datasource.password", RegistrationTestDatabase::password);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @BeforeEach
    void cleanRegistrationData() {
        jdbcTemplate.update("DELETE FROM registrations");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM student_profiles");
        jdbcTemplate.update("DELETE FROM organizer_profiles");
        jdbcTemplate.update("DELETE FROM faculty_profiles");
        jdbcTemplate.update("DELETE FROM admin_profiles");
        jdbcTemplate.update("DELETE FROM users");
    }

    LocalDateTime now() {
        return LocalDateTime.now(RegistrationService.CAMPUS_ZONE);
    }

    long createStudent() {
        return createUser("STUDENT");
    }

    long createOrganizer() {
        return createUser("ORGANIZER");
    }

    long createFaculty() {
        return createUser("FACULTY");
    }

    long createAdmin() {
        return createUser("ADMIN");
    }

    private long createUser(String role) {
        long id = uniqueIds.incrementAndGet();
        jdbcTemplate.update(
            "INSERT INTO users (id, full_name, email, password_hash, department, role, enabled) "
                + "VALUES (?, ?, ?, 'x', 'CSE', ?, TRUE)",
            id,
            "Test " + role + " " + id,
            "user-" + id + "@test.example.edu",
            role
        );
        if ("STUDENT".equals(role)) {
            jdbcTemplate.update(
                "INSERT INTO student_profiles (user_id, roll_number, semester, batch) VALUES (?, ?, 3, 'B')",
                id,
                "ROLL-" + id
            );
        }
        return id;
    }

    long createApprovedEvent(long organizerId, int capacity) {
        return createEvent(organizerId, "APPROVED", now().plusHours(2), capacity);
    }

    long createEvent(long organizerId, String status, LocalDateTime startTime, int capacity) {
        long id = uniqueIds.incrementAndGet();
        jdbcTemplate.update(
            "INSERT INTO events (id, title, description, venue, start_time, end_time, capacity, status, created_by) "
                + "VALUES (?, ?, 'desc', 'Auditorium A', ?, ?, ?, ?, ?)",
            id,
            "Event " + id,
            Timestamp.valueOf(startTime),
            Timestamp.valueOf(startTime.plusHours(3)),
            capacity,
            status,
            organizerId
        );
        return id;
    }

    /** Direct row insert for test setup only (bypassing the service on purpose). */
    void insertRegistrationRow(long studentId, long eventId, RegistrationStatus status) {
        jdbcTemplate.update(
            "INSERT INTO registrations (student_id, event_id, registered_at, status, ticket_no) "
                + "VALUES (?, ?, ?, ?, ?)",
            studentId,
            eventId,
            Timestamp.valueOf(now()),
            status.name(),
            "TEST-" + eventId + "-" + studentId + "-" + uniqueIds.incrementAndGet()
        );
    }

    int confirmedCount(long eventId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status = 'CONFIRMED'",
            Integer.class,
            eventId
        );
        return count == null ? 0 : count;
    }

    RegistrationStatus statusOf(long studentId, long eventId) {
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM registrations WHERE student_id = ? AND event_id = ?",
            String.class,
            studentId,
            eventId
        );
        return status == null ? null : RegistrationStatus.valueOf(status);
    }

    void assertConfirmedNeverExceedsCapacity(long eventId, int capacity) {
        int confirmed = confirmedCount(eventId);
        assertTrue(confirmed <= capacity,
            "CONFIRMED count " + confirmed + " must never exceed capacity " + capacity);
    }

    void assertConfirmedCount(long eventId, int expected) {
        assertEquals(expected, confirmedCount(eventId), "CONFIRMED registration count");
    }
}
