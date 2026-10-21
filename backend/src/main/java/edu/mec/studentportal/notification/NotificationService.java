package edu.mec.studentportal.notification;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns recipient selection and persistence of in-app notifications. Announcement publishing and
 * event cancellation call {@link #notifyAnnouncement} / {@link #notifyEventCancelled} from within
 * their own {@code @Transactional} method so fan-out commits or rolls back with the caller
 * (default propagation REQUIRED joins the caller's transaction). No email/SMS/live push.
 */
@Service
public class NotificationService {
    static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");

    private final NotificationDAO dao;
    private final Clock clock;

    @Autowired
    NotificationService(NotificationDAO dao) {
        this(dao, Clock.system(CAMPUS_ZONE));
    }

    NotificationService(NotificationDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /** Creates one ANNOUNCEMENT notification per enabled user matching the audience role. */
    @Transactional
    public int notifyAnnouncement(long announcementId, String audienceRole, String title, String message) {
        int created = 0;
        for (long recipientId : dao.findEnabledRecipientIds(audienceRole)) {
            created += dao.insertAnnouncementNotification(announcementId, recipientId, title, message);
        }
        return created;
    }

    /**
     * Creates EVENT_CANCELLED notifications for the event's confirmed registrants.
     * The event module must invoke this BEFORE cancelling registrations (or read the recipient
     * list itself) so recipients are still CONFIRMED; it is called on the caller's transaction.
     */
    @Transactional
    public int notifyEventCancelled(long eventId, String title, String message) {
        int created = 0;
        for (long recipientId : dao.findConfirmedRegistrantIds(eventId)) {
            created += dao.insertEventCancelledNotification(eventId, recipientId, title, message);
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(long recipientUserId) {
        return dao.findByRecipient(recipientUserId).stream()
            .map(NotificationResponse::from)
            .toList();
    }

    /** Marks the recipient's own notification read; rejects unknown or foreign notifications. */
    @Transactional
    public NotificationResponse markRead(long notificationId, long recipientUserId) {
        Notification notification = dao.findById(notificationId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                NotificationErrorCodes.NOTIFICATION_NOT_FOUND,
                "The notification does not exist."
            )
        );
        if (notification.recipientUserId() != recipientUserId) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to modify this notification."
            );
        }
        if (notification.readAt() == null) {
            dao.markRead(notificationId, recipientUserId, LocalDateTime.now(clock));
        }
        return dao.findById(notificationId).map(NotificationResponse::from).orElseThrow();
    }
}
