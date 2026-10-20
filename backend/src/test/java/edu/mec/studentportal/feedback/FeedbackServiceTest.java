package edu.mec.studentportal.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.feedback.FeedbackResponses.FeedbackResponse;
import edu.mec.studentportal.feedback.FeedbackResponses.FeedbackSummaryResponse;
import edu.mec.studentportal.testsupport.DatabaseIntegrationTestSupport;
import edu.mec.studentportal.testsupport.TestDatabaseAvailability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/** Feedback submission bounds, eligibility, duplicate and access rules. */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class FeedbackServiceTest extends DatabaseIntegrationTestSupport {
    @Autowired
    FeedbackService feedbackService;

    @Test
    void studentWithConfirmedRegistrationCanSubmitAfterEventEnd() {
        long organizer = createOrganizer();
        long student = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(student, event, "CONFIRMED");

        FeedbackResponse response = feedbackService.submit(event, student, new FeedbackRequest(5, "Great"));

        assertThat(response.id()).isPositive();
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comments()).isEqualTo("Great");
        assertThat(response.studentId()).isEqualTo(student);
        assertThat(response.submittedAt()).isNotNull();
    }

    @Test
    void optionalCommentMayBeNull() {
        long organizer = createOrganizer();
        long student = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(student, event, "CONFIRMED");

        FeedbackResponse response = feedbackService.submit(event, student, new FeedbackRequest(4, null));

        assertThat(response.comments()).isNull();
    }

    @Test
    void rejectsOutOfRangeRatings() {
        long organizer = createOrganizer();
        long student = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(student, event, "CONFIRMED");

        for (int rating : new int[] { 0, 6 }) {
            ApiException exception = assertThrows(
                ApiException.class,
                () -> feedbackService.submit(event, student, new FeedbackRequest(rating, null))
            );
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void rejectsFeedbackBeforeEventEnd() {
        long organizer = createOrganizer();
        long student = createStudent();
        long event = createApprovedEvent(organizer, 5);
        insertRegistration(student, event, "CONFIRMED");

        ApiException exception = assertThrows(
            ApiException.class,
            () -> feedbackService.submit(event, student, new FeedbackRequest(5, null))
        );
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(FeedbackErrorCodes.FEEDBACK_NOT_ALLOWED);
    }

    @Test
    void rejectsFeedbackWithoutConfirmedRegistration() {
        long organizer = createOrganizer();
        long student = createStudent();
        long other = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(student, event, "CANCELLED");
        insertRegistration(other, event, "CONFIRMED");

        for (long ineligible : new long[] { student, createStudent() }) {
            ApiException exception = assertThrows(
                ApiException.class,
                () -> feedbackService.submit(event, ineligible, new FeedbackRequest(5, null))
            );
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode()).isEqualTo(FeedbackErrorCodes.FEEDBACK_NOT_ALLOWED);
        }
    }

    @Test
    void rejectsDuplicateFeedback() {
        long organizer = createOrganizer();
        long student = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(student, event, "CONFIRMED");
        feedbackService.submit(event, student, new FeedbackRequest(5, null));

        ApiException exception = assertThrows(
            ApiException.class,
            () -> feedbackService.submit(event, student, new FeedbackRequest(3, "again"))
        );
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(FeedbackErrorCodes.DUPLICATE_FEEDBACK);
        assertThat(countRows("feedback", "student_id = ? AND event_id = ?", student, event)).isEqualTo(1);
    }

    @Test
    void onlyStudentsMaySubmit() {
        long organizer = createOrganizer();
        long faculty = createFaculty();
        long event = createEndedEvent(organizer, 5);

        for (long actor : new long[] { organizer, faculty }) {
            ApiException exception = assertThrows(
                ApiException.class,
                () -> feedbackService.submit(event, actor, new FeedbackRequest(5, null))
            );
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exception.getCode()).isEqualTo(ApiErrorCodes.FORBIDDEN);
        }
    }

    @Test
    void ownerOrganizerFacultyAdminSeeFullRecordsOthersDenied() {
        long owner = createOrganizer();
        long otherOrganizer = createOrganizer();
        long faculty = createFaculty();
        long admin = createAdmin();
        long student = createStudent();
        long event = createEndedEvent(owner, 5);
        insertRegistration(student, event, "CONFIRMED");
        feedbackService.submit(event, student, new FeedbackRequest(5, "Nice"));

        assertThat(feedbackService.listForEvent(event, owner)).hasSize(1);
        assertThat(feedbackService.listForEvent(event, faculty)).hasSize(1);
        assertThat(feedbackService.listForEvent(event, admin)).hasSize(1);

        ApiException denied = assertThrows(
            ApiException.class,
            () -> feedbackService.listForEvent(event, otherOrganizer)
        );
        assertThat(denied.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void summaryIsVisibleToAnySignedInViewerWithoutStudentIdentity() {
        long organizer = createOrganizer();
        long studentA = createStudent();
        long studentB = createStudent();
        long viewer = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(studentA, event, "CONFIRMED");
        insertRegistration(studentB, event, "CONFIRMED");
        feedbackService.submit(event, studentA, new FeedbackRequest(4, null));
        feedbackService.submit(event, studentB, new FeedbackRequest(2, null));

        FeedbackSummaryResponse summary = feedbackService.summary(event, viewer);

        assertThat(summary.eventId()).isEqualTo(event);
        assertThat(summary.ratingCount()).isEqualTo(2);
        assertThat(summary.averageRating()).isEqualTo(3.0);
    }

    @Test
    void summaryOfEventWithoutFeedbackIsZero() {
        long organizer = createOrganizer();
        long viewer = createStudent();
        long event = createEndedEvent(organizer, 5);

        FeedbackSummaryResponse summary = feedbackService.summary(event, viewer);

        assertThat(summary.ratingCount()).isZero();
        assertThat(summary.averageRating()).isZero();
    }
}
