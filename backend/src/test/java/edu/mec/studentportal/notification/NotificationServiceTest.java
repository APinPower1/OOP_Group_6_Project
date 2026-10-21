package edu.mec.studentportal.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.mec.studentportal.announcement.AnnouncementRequest;
import edu.mec.studentportal.announcement.AnnouncementService;
import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.testsupport.DatabaseIntegrationTestSupport;
import edu.mec.studentportal.testsupport.TestDatabaseAvailability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/** Notification fan-out, read rules and event-cancellation notifications. */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class NotificationServiceTest extends DatabaseIntegrationTestSupport {
    @Autowired
    NotificationService notificationService;

    @Autowired
    AnnouncementService announcementService;

    @Test
    void recipientSeesOnlyTheirOwnNotifications() {
        long organizer = createOrganizer();
        long studentA = createStudent();
        long studentB = createStudent();
        announcementService.publish(new AnnouncementRequest("All", "Body", null), organizer);

        assertThat(notificationService.listMine(studentA)).hasSize(1);
        assertThat(notificationService.listMine(studentB)).hasSize(1);
        assertThat(notificationService.listMine(organizer)).hasSize(1);
    }

    @Test
    void markOwnNotificationReadIsIdempotent() {
        long organizer = createOrganizer();
        long student = createStudent();
        announcementService.publish(new AnnouncementRequest("All", "Body", null), organizer);
        long notificationId = notificationService.listMine(student).get(0).id();

        NotificationResponse firstRead = notificationService.markRead(notificationId, student);
        NotificationResponse secondRead = notificationService.markRead(notificationId, student);

        assertThat(firstRead.readAt()).isNotNull();
        assertThat(secondRead.readAt()).isEqualTo(firstRead.readAt());
    }

    @Test
    void cannotReadOrMarkAnotherUsersNotification() {
        long organizer = createOrganizer();
        long studentA = createStudent();
        long studentB = createStudent();
        announcementService.publish(new AnnouncementRequest("All", "Body", null), organizer);
        long studentANotification = notificationService.listMine(studentA).get(0).id();

        assertThat(notificationService.listMine(studentB)).hasSize(1);

        ApiException forbidden = assertThrows(
            ApiException.class,
            () -> notificationService.markRead(studentANotification, studentB)
        );
        assertThat(forbidden.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getCode()).isEqualTo(ApiErrorCodes.FORBIDDEN);

        ApiException missing = assertThrows(
            ApiException.class,
            () -> notificationService.markRead(999_999L, studentB)
        );
        assertThat(missing.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publishingIsIdempotentPerRecipientAnnouncement() {
        long organizer = createOrganizer();
        long student = createStudent();
        AnnouncementService.PublishResult result = announcementService.publish(
            new AnnouncementRequest("All", "Body", null),
            organizer
        );

        assertThat(countRows(
            "notifications",
            "recipient_user_id = ? AND announcement_id = ?",
            student,
            result.announcement().id()
        )).isEqualTo(1);

        Integer duplicate = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ? AND announcement_id = ?",
            Integer.class,
            student,
            result.announcement().id()
        );
        assertThat(duplicate).isEqualTo(1);
    }

    @Test
    void eventCancellationNotifiesOnlyConfirmedRegistrants() {
        long organizer = createOrganizer();
        long confirmed = createStudent();
        long cancelled = createStudent();
        long event = createApprovedEvent(organizer, 5);
        insertRegistration(confirmed, event, "CONFIRMED");
        insertRegistration(cancelled, event, "CANCELLED");

        int created = notificationService.notifyEventCancelled(event, "Event cancelled", "Sorry!");

        assertThat(created).isEqualTo(1);
        assertThat(countRows("notifications", "recipient_user_id = ? AND event_id = ?", confirmed, event)).isEqualTo(1);
        assertThat(countRows("notifications", "recipient_user_id = ? AND event_id = ?", cancelled, event)).isZero();
        assertThat(countRows("notifications", "notification_type = 'EVENT_CANCELLED'")).isEqualTo(1);
    }
}
