package edu.mec.studentportal.testsupport;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * Shared plumbing for DB-backed module tests against real MySQL. Concrete test classes must carry
 * {@code @EnabledIf(TestDatabaseAvailability#isAvailable)} since conditions are not inherited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAuthSecurityConfig.class)
public abstract class DatabaseIntegrationTestSupport {
    protected static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected MockMvc mockMvc;

    private final AtomicLong uniqueIds = new AtomicLong(System.nanoTime());

    @DynamicPropertySource
    static void testDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabaseAvailability::url);
        registry.add("spring.datasource.username", TestDatabaseAvailability::username);
        registry.add("spring.datasource.password", TestDatabaseAvailability::password);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("spring.flyway.enabled", () -> "true");
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
        return LocalDateTime.now(CAMPUS_ZONE);
    }

    protected long createUser(String role) {
        return createUser(role, true);
    }

    protected long createUser(String role, boolean enabled) {
        long id = uniqueIds.incrementAndGet();
        jdbcTemplate.update(
            "INSERT INTO users (id, full_name, email, password_hash, department, role, enabled) "
                + "VALUES (?, ?, ?, 'x', 'CSE', ?, ?)",
            id,
            "Test " + role + " " + id,
            "user-" + id + "@test.example.edu",
            role,
            enabled
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

    protected long createStudent() {
        return createUser("STUDENT");
    }

    protected long createOrganizer() {
        return createUser("ORGANIZER");
    }

    protected long createFaculty() {
        return createUser("FACULTY");
    }

    protected long createAdmin() {
        return createUser("ADMIN");
    }

    protected long insertClub(String name, String category, boolean active) {
        long id = uniqueIds.incrementAndGet();
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, description, category, is_active) VALUES (?, ?, 'desc', ?, ?)",
            id,
            name,
            category,
            active
        );
        return id;
    }

    protected long insertMembership(long clubId, long studentId, String status) {
        long id = uniqueIds.incrementAndGet();
        jdbcTemplate.update(
            "INSERT INTO club_members (id, club_id, student_id, joined_at, status) VALUES (?, ?, ?, ?, ?)",
            id,
            clubId,
            studentId,
            Timestamp.valueOf(now()),
            status
        );
        return id;
    }

    protected int countRows(String table, String whereSql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + whereSql,
            Integer.class,
            args
        );
        return count == null ? 0 : count;
    }
}
