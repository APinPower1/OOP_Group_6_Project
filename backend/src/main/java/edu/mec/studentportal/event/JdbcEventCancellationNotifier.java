package edu.mec.studentportal.event;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Default EVENT_CANCELLED notification writer used by the event-cancellation transaction.
 * The unique (recipient_user_id, event_id, notification_type) constraint is the last line
 * of defence; a racing duplicate is skipped rather than failing the cancellation.
 *
 * <p>Temporary event-module implementation while Member 6's notification module is pending;
 * replace this bean with their NotificationService-backed implementation when merged.
 */
@Component
class JdbcEventCancellationNotifier implements EventCancellationNotifier {
    private static final Logger log = LoggerFactory.getLogger(JdbcEventCancellationNotifier.class);

    private final JdbcTemplate jdbcTemplate;

    JdbcEventCancellationNotifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void notifyEventCancelled(long eventId, String eventTitle, Collection<Long> recipientUserIds) {
        if (recipientUserIds.isEmpty()) {
            return;
        }
        String title = truncate("Event cancelled: " + eventTitle, 160);
        String message = truncate("The event \"" + eventTitle + "\" has been cancelled.", 1000);
        for (Long recipientId : recipientUserIds) {
            try {
                jdbcTemplate.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO notifications (recipient_user_id, event_id, notification_type, title, message) "
                            + "VALUES (?, ?, 'EVENT_CANCELLED', ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                    );
                    statement.setLong(1, recipientId);
                    statement.setLong(2, eventId);
                    statement.setString(3, title);
                    statement.setString(4, message);
                    return statement;
                });
            } catch (DuplicateKeyException duplicate) {
                log.info(
                    "Skipping duplicate EVENT_CANCELLED notification for recipient {} event {}",
                    recipientId,
                    eventId
                );
            }
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
