package edu.mec.studentportal.announcement;

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

/** Parameterized SQL for announcements. All calls run on the service-managed transaction. */
@Repository
class AnnouncementDAO {
    private static final String COLUMNS =
        "id, title, content, created_by, audience_role, status, created_at, published_at, archived_at";

    private static final RowMapper<Announcement> ROW_MAPPER = (rs, rowNum) -> new Announcement(
        rs.getLong("id"),
        rs.getString("title"),
        rs.getString("content"),
        rs.getLong("created_by"),
        rs.getString("audience_role"),
        AnnouncementStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toLocalDateTime(),
        rs.getTimestamp("archived_at") == null ? null : rs.getTimestamp("archived_at").toLocalDateTime()
    );

    private static final RowMapper<Actor> ACTOR_ROW_MAPPER = (rs, rowNum) ->
        new Actor(rs.getLong("id"), rs.getString("role"), rs.getBoolean("enabled"));

    private final JdbcTemplate jdbcTemplate;

    AnnouncementDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    long insert(String title, String content, long createdBy, String audienceRole, LocalDateTime publishedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO announcements (title, content, created_by, audience_role, status, published_at) "
                    + "VALUES (?, ?, ?, ?, 'PUBLISHED', ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, title);
            statement.setString(2, content);
            statement.setLong(3, createdBy);
            if (audienceRole == null) {
                statement.setNull(4, java.sql.Types.VARCHAR);
            } else {
                statement.setString(4, audienceRole);
            }
            statement.setTimestamp(5, Timestamp.valueOf(publishedAt));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Announcement insert did not return a generated id");
        }
        return key.longValue();
    }

    Optional<Announcement> findById(long id) {
        List<Announcement> rows = jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM announcements WHERE id = ?",
            ROW_MAPPER,
            id
        );
        return rows.stream().findFirst();
    }

    /** Resolves the acting user's role/enabled state for authorization. */
    Optional<Actor> findActor(long userId) {
        List<Actor> rows = jdbcTemplate.query(
            "SELECT id, role, enabled FROM users WHERE id = ?",
            ACTOR_ROW_MAPPER,
            userId
        );
        return rows.stream().findFirst();
    }

    int archive(long id, LocalDateTime archivedAt) {
        return jdbcTemplate.update(
            "UPDATE announcements SET status = 'ARCHIVED', archived_at = ? WHERE id = ?",
            Timestamp.valueOf(archivedAt),
            id
        );
    }

    /** Students see published announcements whose audience is null or their own role. */
    List<Announcement> findVisibleToRole(String role) {
        return jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM announcements "
                + "WHERE status = 'PUBLISHED' AND (audience_role IS NULL OR audience_role = ?) "
                + "ORDER BY published_at DESC, id DESC",
            ROW_MAPPER,
            role
        );
    }

    /** Staff see their own announcements plus every published announcement. */
    List<Announcement> findForStaff(long userId) {
        return jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM announcements "
                + "WHERE status = 'PUBLISHED' OR created_by = ? "
                + "ORDER BY id DESC",
            ROW_MAPPER,
            userId
        );
    }

    /** Acting user's id, role and enabled flag. */
    record Actor(long id, String role, boolean enabled) {}
}
