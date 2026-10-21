package edu.mec.studentportal.notification;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Parameterized SQL for notifications and recipient selection. */
@Repository
class NotificationDAO {
    private static final String COLUMNS =
        "id, recipient_user_id, announcement_id, event_id, notification_type, title, message, created_at, read_at";

    private static final RowMapper<Notification> ROW_MAPPER = (rs, rowNum) -> new Notification(
        rs.getLong("id"),
        rs.getLong("recipient_user_id"),
        rs.getObject("announcement_id", Long.class),
        rs.getObject("event_id", Long.class),
        NotificationType.valueOf(rs.getString("notification_type")),
        rs.getString("title"),
        rs.getString("message"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    NotificationDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Enabled user ids matching the audience: all enabled users, or exactly one role. */
    List<Long> findEnabledRecipientIds(String audienceRole) {
        if (audienceRole == null) {
            return jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE enabled = TRUE ORDER BY id",
                Long.class
            );
        }
        return jdbcTemplate.queryForList(
            "SELECT id FROM users WHERE enabled = TRUE AND role = ? ORDER BY id",
            Long.class,
            audienceRole
        );
    }

    /** Recipient ids holding a confirmed registration for the event (event-cancellation fan-out). */
    List<Long> findConfirmedRegistrantIds(long eventId) {
        return jdbcTemplate.queryForList(
            "SELECT student_id FROM registrations WHERE event_id = ? AND status = 'CONFIRMED' ORDER BY student_id",
            Long.class,
            eventId
        );
    }

    int insertAnnouncementNotification(long announcementId, long recipientUserId, String title, String message) {
        return jdbcTemplate.update(
            "INSERT INTO notifications (recipient_user_id, announcement_id, notification_type, title, message) "
                + "VALUES (?, ?, 'ANNOUNCEMENT', ?, ?)",
            recipientUserId,
            announcementId,
            title,
            message
        );
    }

    int insertEventCancelledNotification(long eventId, long recipientUserId, String title, String message) {
        return jdbcTemplate.update(
            "INSERT INTO notifications (recipient_user_id, event_id, notification_type, title, message) "
                + "VALUES (?, ?, 'EVENT_CANCELLED', ?, ?)",
            recipientUserId,
            eventId,
            title,
            message
        );
    }

    List<Notification> findByRecipient(long recipientUserId) {
        return jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM notifications WHERE recipient_user_id = ? ORDER BY id DESC",
            ROW_MAPPER,
            recipientUserId
        );
    }

    Optional<Notification> findById(long id) {
        List<Notification> rows = jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM notifications WHERE id = ?",
            ROW_MAPPER,
            id
        );
        return rows.stream().findFirst();
    }

    /** Marks a specific recipient's notification read; returns affected rows. */
    int markRead(long id, long recipientUserId, LocalDateTime readAt) {
        return jdbcTemplate.update(
            "UPDATE notifications SET read_at = ? WHERE id = ? AND recipient_user_id = ?",
            Timestamp.valueOf(readAt),
            id,
            recipientUserId
        );
    }
}
