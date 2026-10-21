package edu.mec.studentportal.feedback;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Parameterized SQL for feedback plus the event/registration facts the rules depend on. */
@Repository
class FeedbackDAO {
    private static final RowMapper<Feedback> FEEDBACK_ROW_MAPPER = (rs, rowNum) -> new Feedback(
        rs.getLong("id"),
        rs.getLong("student_id"),
        rs.getLong("event_id"),
        rs.getInt("rating"),
        rs.getString("comments"),
        rs.getTimestamp("submitted_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    FeedbackDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Acting user's role/enabled state. */
    ActorRow findActor(long userId) {
        List<ActorRow> rows = jdbcTemplate.query(
            "SELECT id, role, enabled FROM users WHERE id = ?",
            (rs, rowNum) -> new ActorRow(rs.getLong("id"), rs.getString("role"), rs.getBoolean("enabled")),
            userId
        );
        return rows.stream().findFirst().orElse(null);
    }

    /** Event owner and end time; null when the event does not exist. */
    EventRow findEvent(long eventId) {
        List<EventRow> rows = jdbcTemplate.query(
            "SELECT id, created_by, end_time, status FROM events WHERE id = ?",
            (rs, rowNum) -> new EventRow(
                rs.getLong("id"),
                rs.getLong("created_by"),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getString("status")
            ),
            eventId
        );
        return rows.stream().findFirst().orElse(null);
    }

    boolean hasConfirmedRegistration(long studentId, long eventId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM registrations WHERE student_id = ? AND event_id = ? AND status = 'CONFIRMED'",
            Integer.class,
            studentId,
            eventId
        );
        return count != null && count > 0;
    }

    boolean existsFeedback(long studentId, long eventId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feedback WHERE student_id = ? AND event_id = ?",
            Integer.class,
            studentId,
            eventId
        );
        return count != null && count > 0;
    }

    long insert(long studentId, long eventId, int rating, String comments, LocalDateTime submittedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO feedback (student_id, event_id, rating, comments, submitted_at) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, studentId);
            statement.setLong(2, eventId);
            statement.setInt(3, rating);
            if (comments == null) {
                statement.setNull(4, java.sql.Types.VARCHAR);
            } else {
                statement.setString(4, comments);
            }
            statement.setTimestamp(5, Timestamp.valueOf(submittedAt));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Feedback insert did not return a generated id");
        }
        return key.longValue();
    }

    Feedback findById(long id) {
        List<Feedback> rows = jdbcTemplate.query(
            "SELECT id, student_id, event_id, rating, comments, submitted_at FROM feedback WHERE id = ?",
            FEEDBACK_ROW_MAPPER,
            id
        );
        return rows.stream().findFirst().orElse(null);
    }

    List<Feedback> findByEvent(long eventId) {
        return jdbcTemplate.query(
            "SELECT id, student_id, event_id, rating, comments, submitted_at FROM feedback "
                + "WHERE event_id = ? ORDER BY submitted_at DESC, id DESC",
            FEEDBACK_ROW_MAPPER,
            eventId
        );
    }

    List<Integer> findRatingsForEvent(long eventId) {
        return jdbcTemplate.queryForList(
            "SELECT rating FROM feedback WHERE event_id = ?",
            Integer.class,
            eventId
        );
    }

    /** Acting user's id, role and enabled flag. */
    record ActorRow(long id, String role, boolean enabled) {}

    /** Event owner, end time and status. */
    record EventRow(long id, long createdBy, LocalDateTime endTime, String status) {}
}
