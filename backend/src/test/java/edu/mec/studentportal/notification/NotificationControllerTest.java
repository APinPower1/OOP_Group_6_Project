package edu.mec.studentportal.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.mec.studentportal.announcement.AnnouncementRequest;
import edu.mec.studentportal.announcement.AnnouncementService;
import edu.mec.studentportal.testsupport.DatabaseIntegrationTestSupport;
import edu.mec.studentportal.testsupport.TestAuthSecurityConfig;
import edu.mec.studentportal.testsupport.TestDatabaseAvailability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

/** HTTP contract for notification endpoints. */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class NotificationControllerTest extends DatabaseIntegrationTestSupport {
    @Autowired
    AnnouncementService announcementService;

    @Autowired
    NotificationService notificationService;

    @Test
    void listsOwnNotifications() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        long other = createStudent();
        announcementService.publish(new AnnouncementRequest("All", "Body", null), organizer);

        mockMvc.perform(get("/api/notifications/me")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].notificationType").value("ANNOUNCEMENT"))
            .andExpect(jsonPath("$[0].title").value("All"))
            .andExpect(jsonPath("$[0].announcementId").isNumber())
            .andExpect(jsonPath("$[0].readAt").doesNotExist());

        mockMvc.perform(get("/api/notifications/me")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, other))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void marksOwnNotificationRead() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        announcementService.publish(new AnnouncementRequest("All", "Body", null), organizer);
        long notificationId = notificationService.listMine(student).get(0).id();

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readAt").exists());
    }

    @Test
    void cannotMarkAnotherUsersNotificationRead() throws Exception {
        long organizer = createOrganizer();
        long studentA = createStudent();
        long studentB = createStudent();
        announcementService.publish(new AnnouncementRequest("All", "Body", null), organizer);
        long studentANotification = notificationService.listMine(studentA).get(0).id();

        mockMvc.perform(patch("/api/notifications/{id}/read", studentANotification)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, studentB))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
