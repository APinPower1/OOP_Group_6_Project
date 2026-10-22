package edu.mec.studentportal.report;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Read-only aggregate queries for the admin participation report. */
@Repository
class ReportDAO {
    record ActorRow(long id, String role, boolean enabled) {}

    record EventStatusRow(
        long total,
        long pendingApproval,
        long approved,
        long rejected,
        long cancelled
    ) {}

    record RegistrationStatusRow(long total, long confirmed, long cancelled) {}

    record FeedbackRow(long count, Double averageRating) {}

    private static final RowMapper<ActorRow> ACTOR_ROW_MAPPER = (rs, rowNum) -> new ActorRow(
        rs.getLong("id"),
        rs.getString("role"),
        rs.getBoolean("enabled")
    );

    private static final RowMapper<EventStatusRow> EVENT_STATUS_ROW_MAPPER =
        (rs, rowNum) -> new EventStatusRow(
            rs.getLong("total"),
            rs.getLong("pending_approval"),
            rs.getLong("approved"),
            rs.getLong("rejected"),
            rs.getLong("cancelled")
        );

    private static final RowMapper<RegistrationStatusRow> REGISTRATION_STATUS_ROW_MAPPER =
        (rs, rowNum) -> new RegistrationStatusRow(
            rs.getLong("total"),
            rs.getLong("confirmed"),
            rs.getLong("cancelled")
        );

    private final JdbcTemplate jdbcTemplate;

    ReportDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Acting user's role/enabled state; null when the user no longer exists. */
    ActorRow findActor(long userId) {
        List<ActorRow> rows = jdbcTemplate.query(
            "SELECT id, role, enabled FROM users WHERE id = ?",
            ACTOR_ROW_MAPPER,
            userId
        );
        return rows.stream().findFirst().orElse(null);
    }

    EventStatusRow eventStatusCounts() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(CASE WHEN status = 'PENDING_APPROVAL' THEN 1 ELSE 0 END), 0) AS pending_approval, "
                + "COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved, "
                + "COALESCE(SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected, "
                + "COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled "
                + "FROM events",
            EVENT_STATUS_ROW_MAPPER
        );
    }

    RegistrationStatusRow registrationStatusCounts() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS confirmed, "
                + "COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled "
                + "FROM registrations",
            REGISTRATION_STATUS_ROW_MAPPER
        );
    }

    FeedbackRow feedbackAggregate() {
        return jdbcTemplate.query(
            "SELECT COUNT(*) AS total, AVG(rating) AS average_rating FROM feedback",
            (rs, rowNum) -> new FeedbackRow(
                rs.getLong("total"),
                nullableDouble(rs, "average_rating")
            )
        ).stream().findFirst().orElse(new FeedbackRow(0L, null));
    }

    /**
     * Per-event participation, pre-aggregated per table before joining so registration and
     * feedback rows never multiply each other. Ordered by confirmed seats (ties by id), capped.
     */
    List<ParticipationReportResponse.ParticipationRow> participationRows(int limit) {
        return jdbcTemplate.query(
            "SELECT e.id AS event_id, e.title, e.status, e.capacity, "
                + "COALESCE(rc.confirmed, 0) AS confirmed_count, "
                + "COALESCE(rc.cancelled, 0) AS cancelled_count, "
                + "COALESCE(fc.feedback_count, 0) AS feedback_count, "
                + "fc.average_rating "
                + "FROM events e "
                + "LEFT JOIN ("
                + "  SELECT event_id, "
                + "    SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed, "
                + "    SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled "
                + "  FROM registrations GROUP BY event_id"
                + ") rc ON rc.event_id = e.id "
                + "LEFT JOIN ("
                + "  SELECT event_id, COUNT(*) AS feedback_count, AVG(rating) AS average_rating "
                + "  FROM feedback GROUP BY event_id"
                + ") fc ON fc.event_id = e.id "
                + "ORDER BY confirmed_count DESC, e.id ASC "
                + "LIMIT ?",
            (rs, rowNum) -> new ParticipationReportResponse.ParticipationRow(
                rs.getLong("event_id"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getInt("capacity"),
                rs.getLong("confirmed_count"),
                rs.getLong("cancelled_count"),
                rs.getLong("feedback_count"),
                nullableDouble(rs, "average_rating")
            ),
            limit
        );
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
