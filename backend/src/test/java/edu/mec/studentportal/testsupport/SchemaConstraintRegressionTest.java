package edu.mec.studentportal.testsupport;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataIntegrityViolationException;

@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class SchemaConstraintRegressionTest extends DatabaseIntegrationTestSupport {

    @Test
    void duplicateUserEmailIsRejected() {
        long userId = createUser("STUDENT");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO users (full_name, email, password_hash, department, role, enabled) VALUES (?, ?, 'x', 'CSE', 'STUDENT', TRUE)",
            "Another User",
            "user-" + userId + "@test.example.edu"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateStudentRollNumberIsRejected() {
        long userId1 = createStudent();
        long userId2 = createUser("STUDENT", true); // creates user + profile; replace profile with same roll as userId1
        String roll = jdbcTemplate.queryForObject("SELECT roll_number FROM student_profiles WHERE user_id = ?", String.class, userId1);
        // Remove existing profile for userId2 to test roll number uniqueness
        jdbcTemplate.update("DELETE FROM student_profiles WHERE user_id = ?", userId2);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO student_profiles (user_id, roll_number, semester, batch) VALUES (?, ?, 3, 'B')",
            userId2,
            roll
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateEventRegistrationIsRejected() {
        long organizer = createOrganizer();
        long student = createStudent();
        long eventId = createApprovedEvent(organizer, 10);
        insertRegistration(student, eventId, "CONFIRMED");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO registrations (student_id, event_id, registered_at, status, ticket_no) VALUES (?, ?, NOW(), 'CONFIRMED', ?)",
            student,
            eventId,
            "DUP-" + eventId + "-" + student
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateClubMembershipIsRejected() {
        long clubId = insertClub("TestClub", "Tech", true);
        long student = createStudent();
        insertMembership(clubId, student, "ACTIVE");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO club_members (club_id, student_id, joined_at, status) VALUES (?, ?, NOW(), 'ACTIVE')",
            clubId,
            student
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateFeedbackIsRejected() {
        long organizer = createOrganizer();
        long student = createStudent();
        long eventId = createEndedEvent(organizer, 10);
        insertRegistration(student, eventId, "CONFIRMED");
        jdbcTemplate.update(
            "INSERT INTO feedback (student_id, event_id, rating, comments, submitted_at) VALUES (?, ?, 5, 'ok', NOW())",
            student,
            eventId
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO feedback (student_id, event_id, rating, comments, submitted_at) VALUES (?, ?, 4, 'ok2', NOW())",
            student,
            eventId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidFeedbackRatingIsRejected() {
        long organizer = createOrganizer();
        long student = createStudent();
        long eventId = createEndedEvent(organizer, 10);
        insertRegistration(student, eventId, "CONFIRMED");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO feedback (student_id, event_id, rating, comments, submitted_at) VALUES (?, ?, 6, 'bad', NOW())",
            student,
            eventId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidEventCapacityIsRejected() {
        long organizer = createOrganizer();
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO events (title, description, venue, start_time, end_time, capacity, status, created_by) VALUES (?, 'd', 'v', DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), 0, 'APPROVED', ?)",
            "BadCap",
            organizer
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidEventStatusIsRejected() {
        long organizer = createOrganizer();
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO events (title, description, venue, start_time, end_time, capacity, status, created_by) VALUES (?, 'd', 'v', DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), 10, 'INVALID', ?)",
            "BadStatus",
            organizer
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyViolationIsRejected() {
        long nonExistentUserId = 9223372036854775807L;
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO events (title, description, venue, start_time, end_time, capacity, status, created_by) VALUES (?, 'd', 'v', DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), 10, 'APPROVED', ?)",
            "Orphan",
            nonExistentUserId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteUserWithEventIsRestricted() {
        long organizer = createOrganizer();
        createApprovedEvent(organizer, 5);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", organizer))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
