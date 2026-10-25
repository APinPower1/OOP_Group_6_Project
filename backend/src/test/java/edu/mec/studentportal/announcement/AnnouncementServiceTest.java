package edu.mec.studentportal.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.testsupport.DatabaseIntegrationTestSupport;
import edu.mec.studentportal.testsupport.TestDatabaseAvailability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/** Announcement publishing, audience filtering and archive rules. */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class AnnouncementServiceTest extends DatabaseIntegrationTestSupport {
    @Autowired
    AnnouncementService announcementService;

    @Test
    void publishToAllEnabledUsersCreatesOneNotificationEach() {
        long organizer = createOrganizer();
        long student = createStudent();
        long faculty = createFaculty();
        long disabled = createUser("STUDENT", false);

        AnnouncementService.PublishResult result = announcementService.publish(
            new AnnouncementRequest("Campus Fest", "Registrations open.", null),
            organizer
        );

        assertThat(result.announcement().id()).isPositive();
        assertThat(result.announcement().status()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(result.notificationsCreated()).isEqualTo(3);
        assertThat(countRows("notifications", "announcement_id = ?", result.announcement().id())).isEqualTo(3);
        assertThat(countRows("notifications", "recipient_user_id = ?", disabled)).isZero();
        assertThat(countRows("notifications", "recipient_user_id = ?", student)).isEqualTo(1);
    }

    @Test
    void publishToSingleRoleTargetsOnlyThatRole() {
        long admin = createAdmin();
        long studentA = createStudent();
        long studentB = createStudent();
        createFaculty();
        createOrganizer();

        AnnouncementService.PublishResult result = announcementService.publish(
            new AnnouncementRequest("Exam Notice", "Semester exams next week.", "STUDENT"),
            admin
        );

        assertThat(result.notificationsCreated()).isEqualTo(2);
        assertThat(countRows("notifications", "recipient_user_id = ?", studentA)).isEqualTo(1);
        assertThat(countRows("notifications", "recipient_user_id = ?", studentB)).isEqualTo(1);
        assertThat(countRows("notifications", "notification_type = 'ANNOUNCEMENT'")).isEqualTo(2);
    }

    @Test
    void studentsMayNotPublish() {
        long student = createStudent();

        ApiException exception = assertThrows(
            ApiException.class,
            () -> announcementService.publish(new AnnouncementRequest("X", "Y", null), student)
        );
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getCode()).isEqualTo(ApiErrorCodes.FORBIDDEN);
        assertThat(countRows("announcements", "1 = 1")).isZero();
    }

    @Test
    void facultyMayPublishAndArchiveOwnAnnouncement() {
        long faculty = createFaculty();
        long organizer = createOrganizer();

        AnnouncementService.PublishResult published = announcementService.publish(
            new AnnouncementRequest("Faculty Notice", "Lab sessions rescheduled.", null),
            faculty
        );
        assertThat(published.announcement().status()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(published.announcement().createdBy()).isEqualTo(faculty);

        assertThat(announcementService.archive(published.announcement().id(), faculty).status())
            .isEqualTo(AnnouncementStatus.ARCHIVED);

        AnnouncementService.PublishResult other = announcementService.publish(
            new AnnouncementRequest("Organizer Notice", "Body", null),
            organizer
        );
        ApiException denied = assertThrows(
            ApiException.class,
            () -> announcementService.archive(other.announcement().id(), faculty)
        );
        assertThat(denied.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidAudienceRoleIsRejected() {
        long organizer = createOrganizer();
        createStudent();

        ApiException exception = assertThrows(
            ApiException.class,
            () -> announcementService.publish(new AnnouncementRequest("X", "Y", "PRINCIPAL"), organizer)
        );
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getCode()).isEqualTo(ApiErrorCodes.INVALID_REQUEST);
    }

    @Test
    void archiveByCreatorRetainsNotificationHistory() {
        long organizer = createOrganizer();
        long student = createStudent();
        AnnouncementService.PublishResult published = announcementService.publish(
            new AnnouncementRequest("Update", "Body", null),
            organizer
        );

        AnnouncementResponse archived = announcementService.archive(published.announcement().id(), organizer);

        assertThat(archived.status()).isEqualTo(AnnouncementStatus.ARCHIVED);
        assertThat(archived.archivedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM announcements", Integer.class)).isEqualTo(1);
        assertThat(countRows("notifications", "recipient_user_id = ?", student)).isEqualTo(1);
    }

    @Test
    void onlyCreatorOrganizerOrAdminMayArchive() {
        long owner = createOrganizer();
        long otherOrganizer = createOrganizer();
        long admin = createAdmin();
        AnnouncementService.PublishResult published = announcementService.publish(
            new AnnouncementRequest("Notice", "Body", null),
            owner
        );

        ApiException denied = assertThrows(
            ApiException.class,
            () -> announcementService.archive(published.announcement().id(), otherOrganizer)
        );
        assertThat(denied.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(announcementService.archive(published.announcement().id(), admin).status())
            .isEqualTo(AnnouncementStatus.ARCHIVED);
    }

    @Test
    void studentsSeeOnlyMatchingPublishedAnnouncements() {
        long organizer = createOrganizer();
        long student = createStudent();
        announcementService.publish(new AnnouncementRequest("All", "Body", null), organizer);
        announcementService.publish(new AnnouncementRequest("Students", "Body", "STUDENT"), organizer);
        announcementService.publish(new AnnouncementRequest("Staff", "Body", "FACULTY"), organizer);

        assertThat(announcementService.listVisibleTo(student)).hasSize(2);
        assertThat(announcementService.listVisibleTo(organizer)).hasSize(3);
    }
}
