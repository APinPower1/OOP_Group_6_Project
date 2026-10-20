package edu.mec.studentportal.registration;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Parameterized SQL for the registration module. The events-table queries here are the
 * registration-scoped event access required by the registration transaction (row lock,
 * ownership, capacity); the event module keeps its own DAO for event management.
 * All methods must be called on the transaction/connection managed by the service.
 */
@Repository
class RegistrationDAO {
    static final String EVENT_STATUS_APPROVED = "APPROVED";
    static final String ROLE_STUDENT = "STUDENT";
    static final String ROLE_ORGANIZER = "ORGANIZER";
    static final String ROLE_FACULTY = "FACULTY";
    static final String ROLE_ADMIN = "ADMIN";

    private static final RowMapper<EventLockRow> EVENT_LOCK_ROW_MAPPER =
        (rs, rowNum) -> new EventLockRow(
            rs.getLong("id"),
            rs.getString("status"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getInt("capacity"),
            rs.getLong("created_by")
        );

    private static final RowMapper<Registration> REGISTRATION_ROW_MAPPER =
        (rs, rowNum) -> new Registration(
            rs.getLong("id"),
            rs.getLong("student_id"),
            rs.getLong("event_id"),
            rs.getTimestamp("registered_at").toLocalDateTime(),
            RegistrationStatus.valueOf(rs.getString("status")),
            rs.getString("ticket_no")
        );

    private static final RowMapper<UserRoleRow> USER_ROLE_ROW_MAPPER =
        (rs, rowNum) -> new UserRoleRow(rs.getLong("id"), rs.getString("role"), rs.getBoolean("enabled"));

    private static final RowMapper<RosterRow> ROSTER_ROW_MAPPER =
        (rs, rowNum) -> new RosterRow(
            rs.getLong("id"),
            rs.getLong("student_id"),
            rs.getString("full_name"),
            rs.getString("roll_number"),
            RegistrationStatus.valueOf(rs.getString("status")),
            rs.getString("ticket_no"),
            rs.getTimestamp("registered_at").toLocalDateTime()
        );

    private final JdbcTemplate jdbcTemplate;

    RegistrationDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Locks the target event row for the rest of the transaction so concurrent
     * registrations of the same event serialize on capacity/duplicate checks.
     */
    Optional<EventLockRow> lockEvent(long eventId) {
        List<EventLockRow> rows = jdbcTemplate.query(
            "SELECT id, status, start_time, capacity, created_by FROM events WHERE id = ? FOR UPDATE",
            EVENT_LOCK_ROW_MAPPER,
            eventId
        );
        return rows.stream().findFirst();
    }

    /** Unlocked read for read-only paths; registration writes must use {@link #lockEvent}. */
    Optional<EventLockRow> findEvent(long eventId) {
        List<EventLockRow> rows = jdbcTemplate.query(
            "SELECT id, status, start_time, capacity, created_by FROM events WHERE id = ?",
            EVENT_LOCK_ROW_MAPPER,
            eventId
        );
        return rows.stream().findFirst();
    }

    Optional<UserRoleRow> findUser(long userId) {
        List<UserRoleRow> rows = jdbcTemplate.query(
            "SELECT id, role, enabled FROM users WHERE id = ?",
            USER_ROLE_ROW_MAPPER,
            userId
        );
        return rows.stream().findFirst();
    }

    Optional<Registration> findByStudentAndEvent(long studentId, long eventId) {
        List<Registration> rows = jdbcTemplate.query(
            "SELECT id, student_id, event_id, registered_at, status, ticket_no "
                + "FROM registrations WHERE student_id = ? AND event_id = ?",
            REGISTRATION_ROW_MAPPER,
            studentId,
            eventId
        );
        return rows.stream().findFirst();
    }

    int countConfirmed(long eventId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status = 'CONFIRMED'",
            Integer.class,
            eventId
        );
        return count == null ? 0 : count;
    }

    /** Throws {@link DuplicateKeyException} if the unique constraints are violated by a racing writer. */
    long insert(long studentId, long eventId, String ticketNo, LocalDateTime registeredAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO registrations (student_id, event_id, registered_at, status, ticket_no) "
                    + "VALUES (?, ?, ?, 'CONFIRMED', ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, studentId);
            statement.setLong(2, eventId);
            statement.setTimestamp(3, Timestamp.valueOf(registeredAt));
            statement.setString(4, ticketNo);
            return statement;
        }, keyHolder);
        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("Registration insert did not return a generated id");
        }
        return generatedKey.longValue();
    }

    /** Reactivates a cancelled row in place with a new ticket number and timestamp. */
    void reactivate(long registrationId, String ticketNo, LocalDateTime registeredAt) {
        jdbcTemplate.update(
            "UPDATE registrations SET status = 'CONFIRMED', ticket_no = ?, registered_at = ? WHERE id = ?",
            ticketNo,
            Timestamp.valueOf(registeredAt),
            registrationId
        );
    }

    void updateStatus(long registrationId, RegistrationStatus status) {
        jdbcTemplate.update(
            "UPDATE registrations SET status = ? WHERE id = ?",
            status.name(),
            registrationId
        );
    }

    /** Event-cancellation contract for the event module: cancels every confirmed row for the event. */
    int cancelConfirmedForEvent(long eventId) {
        return jdbcTemplate.update(
            "UPDATE registrations SET status = 'CANCELLED' WHERE event_id = ? AND status = 'CONFIRMED'",
            eventId
        );
    }

    List<Registration> findByStudent(long studentId) {
        return jdbcTemplate.query(
            "SELECT id, student_id, event_id, registered_at, status, ticket_no "
                + "FROM registrations WHERE student_id = ? ORDER BY id DESC",
            REGISTRATION_ROW_MAPPER,
            studentId
        );
    }

    List<RosterRow> findRoster(long eventId) {
        return jdbcTemplate.query(
            "SELECT r.id, r.student_id, u.full_name, sp.roll_number, r.status, r.ticket_no, r.registered_at "
                + "FROM registrations r "
                + "JOIN users u ON u.id = r.student_id "
                + "JOIN student_profiles sp ON sp.user_id = r.student_id "
                + "WHERE r.event_id = ? ORDER BY r.id",
            ROSTER_ROW_MAPPER,
            eventId
        );
    }

    record EventLockRow(long id, String status, LocalDateTime startTime, int capacity, long createdBy) {}

    record UserRoleRow(long id, String role, boolean enabled) {}

    record RosterRow(
        long registrationId,
        long studentId,
        String fullName,
        String rollNumber,
        RegistrationStatus status,
        String ticketNo,
        LocalDateTime registeredAt
    ) {}
}
