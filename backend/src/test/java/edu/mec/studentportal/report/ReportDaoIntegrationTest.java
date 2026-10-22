package edu.mec.studentportal.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.testsupport.DatabaseIntegrationTestSupport;
import edu.mec.studentportal.testsupport.TestDatabaseAvailability;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class ReportDaoIntegrationTest extends DatabaseIntegrationTestSupport {

    @Autowired
    private ReportService reportService;

    @Test
    void aggregatesEventRegistrationAndFeedbackCounts() {
        long adminId = createAdmin();
        long organizerId = createOrganizer();
        long student1 = createStudent();
        long student2 = createStudent();

        long approvedEvent = createApprovedEvent(organizerId, 50);
        long pendingEvent = createEvent(organizerId, "PENDING_APPROVAL", now().plusDays(2), 30);
        insertRegistration(student1, approvedEvent, "CONFIRMED");
        insertRegistration(student2, approvedEvent, "CANCELLED");
        insertFeedback(student1, approvedEvent, 5);
        insertFeedback(student2, approvedEvent, 4);

        ParticipationReportResponse report = reportService.participationReport(adminId);

        assertThat(report.events().total()).isEqualTo(2);
        assertThat(report.events().approved()).isEqualTo(1);
        assertThat(report.events().pendingApproval()).isEqualTo(1);
        assertThat(report.events().rejected()).isZero();
        assertThat(report.events().cancelled()).isZero();
        assertThat(report.registrations().total()).isEqualTo(2);
        assertThat(report.registrations().confirmed()).isEqualTo(1);
        assertThat(report.registrations().cancelled()).isEqualTo(1);
        assertThat(report.feedback().count()).isEqualTo(2);
        assertThat(report.feedback().averageRating()).isEqualTo(4.5);

        assertThat(report.participation()).hasSize(2);
        ParticipationReportResponse.ParticipationRow approvedRow = report.participation().stream()
            .filter(row -> row.eventId() == approvedEvent)
            .findFirst()
            .orElseThrow();
        assertThat(approvedRow.confirmedCount()).isEqualTo(1);
        assertThat(approvedRow.cancelledCount()).isEqualTo(1);
        assertThat(approvedRow.feedbackCount()).isEqualTo(2);
        assertThat(approvedRow.averageRating()).isEqualTo(4.5);
        assertThat(approvedRow.status()).isEqualTo("APPROVED");
    }

    @Test
    void registrationAndFeedbackJoinsDoNotMultiplyCounts() {
        long adminId = createAdmin();
        long organizerId = createOrganizer();
        long student1 = createStudent();
        long student2 = createStudent();
        long student3 = createStudent();

        long eventId = createApprovedEvent(organizerId, 30);
        insertRegistration(student1, eventId, "CONFIRMED");
        insertRegistration(student2, eventId, "CONFIRMED");
        insertRegistration(student3, eventId, "CONFIRMED");
        insertFeedback(student1, eventId, 3);
        insertFeedback(student2, eventId, 5);

        ParticipationReportResponse report = reportService.participationReport(adminId);

        ParticipationReportResponse.ParticipationRow row = report.participation().get(0);
        assertThat(row.confirmedCount()).isEqualTo(3);
        assertThat(row.cancelledCount()).isZero();
        assertThat(row.feedbackCount()).isEqualTo(2);
        assertThat(row.averageRating()).isEqualTo(4.0);
    }

    @Test
    void emptyDatabaseYieldsZeroCountsAndNullAverage() {
        long adminId = createAdmin();

        ParticipationReportResponse report = reportService.participationReport(adminId);

        assertThat(report.events().total()).isZero();
        assertThat(report.registrations().total()).isZero();
        assertThat(report.feedback().count()).isZero();
        assertThat(report.feedback().averageRating()).isNull();
        assertThat(report.participation()).isEmpty();
    }

    @Test
    void participationRowsAreOrderedByConfirmedSeatsThenId() {
        long adminId = createAdmin();
        long organizerId = createOrganizer();
        long student1 = createStudent();
        long student2 = createStudent();
        long student3 = createStudent();

        long fewerSeats = createApprovedEvent(organizerId, 10);
        long moreSeats = createApprovedEvent(organizerId, 10);
        insertRegistration(student1, fewerSeats, "CONFIRMED");
        insertRegistration(student1, moreSeats, "CANCELLED");
        insertRegistration(student2, moreSeats, "CONFIRMED");
        insertRegistration(student3, moreSeats, "CONFIRMED");

        List<ParticipationReportResponse.ParticipationRow> rows =
            reportService.participationReport(adminId).participation();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).eventId()).isEqualTo(moreSeats);
        assertThat(rows.get(0).confirmedCount()).isEqualTo(2);
        assertThat(rows.get(1).eventId()).isEqualTo(fewerSeats);
        assertThat(rows.get(1).confirmedCount()).isEqualTo(1);
    }

    @Test
    void disabledAdminCannotReadReport() {
        long adminId = createUser("ADMIN", false);

        assertThatThrownBy(() -> reportService.participationReport(adminId))
            .isInstanceOf(ApiException.class)
            .hasMessage("Your account is disabled.");
    }

    @Test
    void studentCannotReadReport() {
        long studentId = createStudent();

        assertThatThrownBy(() -> reportService.participationReport(studentId))
            .isInstanceOf(ApiException.class)
            .hasMessage("Administrator access is required.");
    }

    private void insertFeedback(long studentId, long eventId, int rating) {
        jdbcTemplate.update(
            "INSERT INTO feedback (student_id, event_id, rating, comments, submitted_at) "
                + "VALUES (?, ?, ?, 'Great event', ?)",
            studentId,
            eventId,
            rating,
            Timestamp.valueOf(now())
        );
    }
}
