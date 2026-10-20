package edu.mec.studentportal.event;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Parameterized SQL for the event module. Venue conflict uses normalized venue equality
 * (trim + case-insensitive) and strict time-range overlap; only PENDING_APPROVAL and
 * APPROVED events occupy a venue. All methods must be called on the transaction/connection
 * managed by the service.
 */
@Repository
class EventDAO {
    static final String ROLE_STUDENT = "STUDENT";
    static final String ROLE_ORGANIZER = "ORGANIZER";
    static final String ROLE_FACULTY = "FACULTY";
    static final String ROLE_ADMIN = "ADMIN";

    private static final String EVENT_COLUMNS =
        "id, title, description, venue, start_time, end_time, capacity, status, "
            + "created_by, reviewed_by, review_reason, created_at, updated_at";

    private static final RowMapper<Event> EVENT_ROW_MAPPER =
        (rs, rowNum) -> new Event(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("venue"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getInt("capacity"),
            EventStatus.valueOf(rs.getString("status")),
            rs.getLong("created_by"),
            readLongNullable(rs.getObject("reviewed_by")),
            rs.getString("review_reason"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );

    private static final RowMapper<UserRoleRow> USER_ROLE_ROW_MAPPER =
        (rs, rowNum) -> new UserRoleRow(rs.getLong("id"), rs.getString("role"), rs.getBoolean("enabled"));

    private final JdbcTemplate jdbcTemplate;

    EventDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Locks the target event row for the rest of the transaction so concurrent approve,
     * reject, cancel and edit transitions serialize on the same row.
     */
    Optional<Event> lockById(long eventId) {
        List<Event> rows = jdbcTemplate.query(
            "SELECT " + EVENT_COLUMNS + " FROM events WHERE id = ? FOR UPDATE",
            EVENT_ROW_MAPPER,
            eventId
        );
        return rows.stream().findFirst();
    }

    /** Unlocked read for read-only paths; state transitions must use {@link #lockById}. */
    Optional<Event> findById(long eventId) {
        List<Event> rows = jdbcTemplate.query(
            "SELECT " + EVENT_COLUMNS + " FROM events WHERE id = ?",
            EVENT_ROW_MAPPER,
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

    long insert(
        String title,
        String description,
        String venue,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int capacity,
        long createdBy
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO events (title, description, venue, start_time, end_time, capacity, status, created_by) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'PENDING_APPROVAL', ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setString(3, venue);
            statement.setTimestamp(4, Timestamp.valueOf(startTime));
            statement.setTimestamp(5, Timestamp.valueOf(endTime));
            statement.setInt(6, capacity);
            statement.setLong(7, createdBy);
            return statement;
        }, keyHolder);
        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("Event insert did not return a generated id");
        }
        return generatedKey.longValue();
    }

    /** Updates proposal details and resubmits the event as PENDING_APPROVAL, clearing review data. */
    void updateDetails(
        long eventId,
        String title,
        String description,
        String venue,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int capacity
    ) {
        jdbcTemplate.update(
            "UPDATE events SET title = ?, description = ?, venue = ?, start_time = ?, end_time = ?, "
                + "capacity = ?, status = 'PENDING_APPROVAL', reviewed_by = NULL, review_reason = NULL "
                + "WHERE id = ?",
            title,
            description,
            venue,
            Timestamp.valueOf(startTime),
            Timestamp.valueOf(endTime),
            capacity,
            eventId
        );
    }

    void markApproved(long eventId, long reviewedBy) {
        jdbcTemplate.update(
            "UPDATE events SET status = 'APPROVED', reviewed_by = ?, review_reason = NULL WHERE id = ?",
            reviewedBy,
            eventId
        );
    }

    void markRejected(long eventId, long reviewedBy, String reason) {
        jdbcTemplate.update(
            "UPDATE events SET status = 'REJECTED', reviewed_by = ?, review_reason = ? WHERE id = ?",
            reviewedBy,
            reason,
            eventId
        );
    }

    void markCancelled(long eventId) {
        jdbcTemplate.update(
            "UPDATE events SET status = 'CANCELLED' WHERE id = ?",
            eventId
        );
    }

    /**
     * Returns an event that occupies the same normalized venue with an overlapping time range.
     * Strict inequalities mean back-to-back events (one ends exactly when another starts) clash.
     * Rejected and cancelled events no longer occupy the venue.
     */
    Optional<Event> findVenueConflict(
        String venue,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long excludeEventId
    ) {
        List<Event> rows = jdbcTemplate.query(
            "SELECT " + EVENT_COLUMNS + " FROM events "
                + "WHERE LOWER(TRIM(venue)) = LOWER(TRIM(?)) "
                + "AND start_time < ? AND end_time > ? "
                + "AND status IN ('PENDING_APPROVAL', 'APPROVED') "
                + "AND id <> ?",
            EVENT_ROW_MAPPER,
            venue,
            Timestamp.valueOf(endTime),
            Timestamp.valueOf(startTime),
            excludeEventId
        );
        return rows.stream().findFirst();
    }

    /** CONFIRMED registration count for one event; never a stored column. */
    int countConfirmed(long eventId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status = 'CONFIRMED'",
            Integer.class,
            eventId
        );
        return count == null ? 0 : count;
    }

    /** Batch CONFIRMED counts for list responses; avoids a per-row count query. */
    Map<Long, Integer> countConfirmedByEventIds(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(eventIds.size(), "?"));
        Object[] arguments = eventIds.toArray();
        Map<Long, Integer> counts = new HashMap<>();
        jdbcTemplate.query(
            "SELECT event_id, COUNT(*) AS cnt FROM registrations "
                + "WHERE status = 'CONFIRMED' AND event_id IN (" + placeholders + ") "
                + "GROUP BY event_id",
            rs -> {
                counts.put(rs.getLong("event_id"), rs.getInt("cnt"));
            },
            arguments
        );
        return counts;
    }

    /** Confirmed registrant user ids for event-cancellation notifications. */
    List<Long> findConfirmedRegistrantUserIds(long eventId) {
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT student_id FROM registrations WHERE event_id = ? AND status = 'CONFIRMED'",
            Long.class,
            eventId
        );
    }

    /**
     * Role-scoped event search. Students only ever see APPROVED events regardless of filters;
     * organizers see their own proposals plus APPROVED events; Faculty/Admin see everything.
     * Dynamic clauses are allow-listed fragments; every value is a bound parameter.
     */
    List<Event> search(
        long requesterId,
        String role,
        String query,
        LocalDateTime from,
        LocalDateTime to,
        EventStatus statusFilter
    ) {
        List<String> clauses = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();

        switch (role) {
            case ROLE_STUDENT -> clauses.add("status = 'APPROVED'");
            case ROLE_ORGANIZER -> {
                clauses.add("(created_by = ? OR status = 'APPROVED')");
                arguments.add(requesterId);
            }
            case ROLE_FACULTY, ROLE_ADMIN -> { }
            default -> clauses.add("1 = 0");
        }

        if (statusFilter != null && !ROLE_STUDENT.equals(role)) {
            clauses.add("status = ?");
            arguments.add(statusFilter.name());
        }
        if (query != null && !query.isBlank()) {
            clauses.add("(LOWER(title) LIKE ? OR LOWER(venue) LIKE ?)");
            String pattern = "%" + query.trim().toLowerCase() + "%";
            arguments.add(pattern);
            arguments.add(pattern);
        }
        if (from != null) {
            clauses.add("start_time >= ?");
            arguments.add(Timestamp.valueOf(from));
        }
        if (to != null) {
            clauses.add("start_time <= ?");
            arguments.add(Timestamp.valueOf(to));
        }

        String where = clauses.isEmpty() ? "1 = 1" : String.join(" AND ", clauses);
        String sql = "SELECT " + EVENT_COLUMNS + " FROM events WHERE " + where
            + " ORDER BY start_time ASC, id ASC";
        return jdbcTemplate.query(sql, EVENT_ROW_MAPPER, arguments.toArray());
    }

    private static Long readLongNullable(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    record UserRoleRow(long id, String role, boolean enabled) {}
}
