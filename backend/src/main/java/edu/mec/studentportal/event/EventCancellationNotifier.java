package edu.mec.studentportal.event;

import java.util.Collection;

/**
 * Collaborator for the event-cancellation transaction. Implementations must create one
 * EVENT_CANCELLED notification row per confirmed registrant inside the caller's active
 * transaction (docs/DATA_MODEL.md note 5) so event status, registration cancellation and
 * notifications commit or roll back together.
 *
 * <p>Member 6 owns the notification module; replace the default JDBC implementation with
 * their NotificationService-backed bean when that module lands.
 */
public interface EventCancellationNotifier {
    void notifyEventCancelled(long eventId, String eventTitle, Collection<Long> recipientUserIds);
}
