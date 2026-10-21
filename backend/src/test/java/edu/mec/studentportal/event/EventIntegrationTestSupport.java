package edu.mec.studentportal.event;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Shared plumbing for event integration tests against a real MySQL database
 * (FOR UPDATE and unique-constraint semantics cannot be faked with an embedded DB).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EventTestSecurityConfig.class)
abstract class EventIntegrationTestSupport {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EventService eventService;

    @Autowired
    MockMvc mockMvc;

    private final AtomicLong uniqueIds = new AtomicLong(System.nanoTime());

    @DynamicPropertySource
    static void eventTestDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", EventTestDatabase::url);
        registry.add("spring.datasource.username", EventTestDatabase::username);
        registry.add("spring.datasource.password", EventTestDatabase::password);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @BeforeEach
    void cleanEventData() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM registrations");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM student_profiles");
        jdbcTemplate.update("DELETE FROM organizer_profiles");
        jdbcTemplate.update("DELETE FROM faculty_profiles");
        jdbcTemplate.update("DELETE FROM admin_profiles");
        jdbcTemplate.update("DELETE FROM users");
    }

    LocalDateTime now() {
        return LocalDateTime.now(EventService.CAMPUS_ZONE).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
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

    /** Direct row insert for test setup only (bypassing the service on purpose). */
    long insertEventRow(
        long organizerId,
        String status,
        String venue,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int capacity
    ) {
        long id = uniqueIds.incrementAndGet();
        jdbcTemplate.update(
            "INSERT INTO events (id, title, description, venue, start_time, end_time, capacity, status, created_by) "
                + "VALUES (?, ?, 'desc', ?, ?, ?, ?, ?, ?)",
            id,
            "Event " + id,
            venue,
            Timestamp.valueOf(startTime),
            Timestamp.valueOf(endTime),
            capacity,
            status,
            organizerId
        );
        return id;
    }

    long insertEventRow(long organizerId, String status, LocalDateTime startTime, int capacity) {
        return insertEventRow(
            organizerId,
            status,
            "Auditorium A",
            startTime,
            startTime.plusHours(3),
            capacity
        );
    }

    long insertEventRow(
        long organizerId,
        String status,
        String venue,
        LocalDateTime startTime,
        int capacity
    ) {
        return insertEventRow(
            organizerId,
            status,
            venue,
            startTime,
            startTime.plusHours(3),
            capacity
        );
    }

    /** Direct registration insert for test setup only. */
    void insertRegistrationRow(long studentId, long eventId, String status) {
        jdbcTemplate.update(
            "INSERT INTO registrations (student_id, event_id, registered_at, status, ticket_no) "
                + "VALUES (?, ?, ?, ?, ?)",
            studentId,
            eventId,
            Timestamp.valueOf(now()),
            status,
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

    int eventCancelledNotificationCount(long eventId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE event_id = ? AND notification_type = 'EVENT_CANCELLED'",
            Integer.class,
            eventId
        );
        return count == null ? 0 : count;
    }

    String eventStatus(long eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM events WHERE id = ?",
            String.class,
            eventId
        );
    }

    void assertEventStatus(long eventId, String expected) {
        assertThat(eventStatus(eventId)).isEqualTo(expected);
    }
}
