package edu.mec.studentportal.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import edu.mec.studentportal.notification.NotificationService;
import edu.mec.studentportal.testsupport.DatabaseIntegrationTestSupport;
import edu.mec.studentportal.testsupport.TestDatabaseAvailability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Publishing must commit the announcement and its notification fan-out atomically: if fan-out
 * fails, the announcement row must roll back with it. The real transactional AnnouncementService
 * is used with only its NotificationService collaborator replaced by a failing mock.
 */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class AnnouncementPublishTransactionTest extends DatabaseIntegrationTestSupport {
    @Autowired
    AnnouncementService announcementService;

    @MockBean
    NotificationService notificationService;

    @Test
    void announcementRollsBackWhenNotificationFanOutFails() {
        long organizer = createOrganizer();
        createStudent();
        doThrow(new IllegalStateException("fan-out down"))
            .when(notificationService)
            .notifyAnnouncement(anyLong(), any(), anyString(), anyString());
        int announcementsBefore = countRows("announcements", "1 = 1");
        int notificationsBefore = countRows("notifications", "1 = 1");

        assertThatThrownBy(() -> announcementService.publish(
            new AnnouncementRequest("Will Fail", "Body", null),
            organizer
        )).isInstanceOf(IllegalStateException.class);

        assertThat(countRows("announcements", "1 = 1")).isEqualTo(announcementsBefore);
        assertThat(countRows("notifications", "1 = 1")).isEqualTo(notificationsBefore);
    }
}
