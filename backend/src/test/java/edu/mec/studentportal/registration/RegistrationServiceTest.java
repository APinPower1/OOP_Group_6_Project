package edu.mec.studentportal.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/** Critical registration business rules against a real MySQL database. */
@EnabledIf(
    value = "edu.mec.studentportal.registration.RegistrationTestDatabase#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class RegistrationServiceTest extends RegistrationIntegrationTestSupport {
    @Autowired
    RegistrationService service;

    @Test
    void registersStudentAndIssuesTicket() {
        long student = createStudent();
        long event = createApprovedEvent(createOrganizer(), 10);

        RegistrationResponse response = service.register(event, student);

        assertThat(response.registrationId()).isPositive();
        assertThat(response.eventId()).isEqualTo(event);
        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(response.ticketNo()).startsWith("EVT-" + event + "-").hasSizeLessThanOrEqualTo(80);
        assertThat(response.registeredCount()).isEqualTo(1);
        assertThat(response.availableSlots()).isEqualTo(9);
        assertThat(response.registeredAt()).isNotNull();
        assertThat(statusOf(student, event)).isEqualTo(RegistrationStatus.CONFIRMED);
        assertConfirmedCount(event, 1);
    }

    @Test
    void rejectsDuplicateRegistrationWith409() {
        long student = createStudent();
        long event = createApprovedEvent(createOrganizer(), 10);
        service.register(event, student);

        ApiException exception = assertThrows(ApiException.class, () -> service.register(event, student));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(RegistrationErrorCodes.DUPLICATE_REGISTRATION);
        assertConfirmedCount(event, 1);
    }

    @Test
    void rejectsRegistrationWhenEventIsFull() {
        long event = createApprovedEvent(createOrganizer(), 1);
        long first = createStudent();
        long second = createStudent();
        service.register(event, first);

        ApiException exception = assertThrows(ApiException.class, () -> service.register(event, second));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(RegistrationErrorCodes.EVENT_FULL);
        assertConfirmedNeverExceedsCapacity(event, 1);
        assertConfirmedCount(event, 1);
    }

    @Test
    void rejectsPendingRejectedAndCancelledEvents() {
        long organizer = createOrganizer();
        long student = createStudent();

        long pending = createEvent(organizer, "PENDING_APPROVAL", now().plusHours(2), 5);
        long rejected = createEvent(organizer, "REJECTED", now().plusHours(2), 5);
        long cancelled = createEvent(organizer, "CANCELLED", now().plusHours(2), 5);

        for (long event : new long[] { pending, rejected, cancelled }) {
            ApiException exception = assertThrows(ApiException.class, () -> service.register(event, student));
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode()).isEqualTo(RegistrationErrorCodes.EVENT_NOT_APPROVED);
        }
    }

    @Test
    void rejectsRegistrationForPastOrStartedEvent() {
        long student = createStudent();
        long event = createEvent(createOrganizer(), "APPROVED", now().minusHours(1), 5);

        ApiException exception = assertThrows(ApiException.class, () -> service.register(event, student));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(RegistrationErrorCodes.REGISTRATION_CLOSED);
    }

    @Test
    void rejectsRegistrationForUnknownEvent() {
        long student = createStudent();

        ApiException exception = assertThrows(ApiException.class, () -> service.register(999_999L, student));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo(RegistrationErrorCodes.EVENT_NOT_FOUND);
    }

    @Test
    void rejectsNonStudentAndDisabledStudent() {
        long event = createApprovedEvent(createOrganizer(), 5);
        long organizer = createOrganizer();
        long faculty = createFaculty();
        long admin = createAdmin();
        long disabledStudent = createStudent();
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", disabledStudent);

        for (long actor : new long[] { organizer, faculty, admin, disabledStudent }) {
            ApiException exception = assertThrows(ApiException.class, () -> service.register(event, actor));
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exception.getCode()).isEqualTo(ApiErrorCodes.FORBIDDEN);
        }
    }

    @Test
    void cancellationFreesSeatAndAllowsAnotherStudentToRegister() {
        long event = createApprovedEvent(createOrganizer(), 1);
        long first = createStudent();
        long second = createStudent();
        service.register(event, first);

        RegistrationResponse cancelled = service.cancel(event, first);

        assertThat(cancelled.status()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(cancelled.registeredCount()).isZero();
        assertThat(cancelled.availableSlots()).isEqualTo(1);
        assertThat(statusOf(first, event)).isEqualTo(RegistrationStatus.CANCELLED);

        RegistrationResponse reRegistered = service.register(event, second);
        assertThat(reRegistered.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertConfirmedCount(event, 1);
    }

    @Test
    void reactivationReusesRowWithNewTicketAndConsumesOneSeat() {
        long event = createApprovedEvent(createOrganizer(), 1);
        long student = createStudent();
        long other = createStudent();
        RegistrationResponse original = service.register(event, student);
        service.cancel(event, student);
        service.register(event, other);

        ApiException full = assertThrows(ApiException.class, () -> service.register(event, student));
        assertThat(full.getCode()).isEqualTo(RegistrationErrorCodes.EVENT_FULL);

        service.cancel(event, other);
        RegistrationResponse reactivated = service.register(event, student);

        assertThat(reactivated.registrationId()).isEqualTo(original.registrationId());
        assertThat(reactivated.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(reactivated.ticketNo()).isNotEqualTo(original.ticketNo());
        assertThat(reactivated.registeredAt()).isAfterOrEqualTo(original.registeredAt());
        assertThat(reactivated.registeredCount()).isEqualTo(1);
        assertThat(reactivated.availableSlots()).isZero();
        assertConfirmedNeverExceedsCapacity(event, 1);
        assertConfirmedCount(event, 1);
        Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM registrations WHERE student_id = ? AND event_id = ?",
            Integer.class,
            student,
            event
        );
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void cancelOnlyOwnRegistrationAndRejectsUnknownOrRepeated() {
        long event = createApprovedEvent(createOrganizer(), 5);
        long studentA = createStudent();
        long studentB = createStudent();
        service.register(event, studentA);
        service.register(event, studentB);

        service.cancel(event, studentB);

        assertThat(statusOf(studentA, event)).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(statusOf(studentB, event)).isEqualTo(RegistrationStatus.CANCELLED);

        ApiException repeated = assertThrows(ApiException.class, () -> service.cancel(event, studentB));
        assertThat(repeated.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repeated.getCode()).isEqualTo(RegistrationErrorCodes.REGISTRATION_ALREADY_CANCELLED);

        long stranger = createStudent();
        ApiException missing = assertThrows(ApiException.class, () -> service.cancel(event, stranger));
        assertThat(missing.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getCode()).isEqualTo(RegistrationErrorCodes.REGISTRATION_NOT_FOUND);
    }

    @Test
    void cancelAfterEventStartIsRejected() {
        long student = createStudent();
        long event = createEvent(createOrganizer(), "APPROVED", now().minusHours(2), 5);
        insertRegistrationRow(student, event, RegistrationStatus.CONFIRMED);

        ApiException exception = assertThrows(ApiException.class, () -> service.cancel(event, student));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(RegistrationErrorCodes.REGISTRATION_CLOSED);
        assertThat(statusOf(student, event)).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void eventCancellationContractCancelsAllConfirmedRegistrations() {
        long event = createApprovedEvent(createOrganizer(), 10);
        long studentA = createStudent();
        long studentB = createStudent();
        long studentC = createStudent();
        service.register(event, studentA);
        service.register(event, studentB);
        insertRegistrationRow(studentC, event, RegistrationStatus.CANCELLED);

        int cancelledRows = service.cancelAllConfirmedForEvent(event);

        assertThat(cancelledRows).isEqualTo(2);
        assertThat(statusOf(studentA, event)).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(statusOf(studentB, event)).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(statusOf(studentC, event)).isEqualTo(RegistrationStatus.CANCELLED);
        assertConfirmedCount(event, 0);
    }

    @Test
    void listsOwnHistoryIncludingCancelledRows() {
        long student = createStudent();
        long other = createStudent();
        long eventA = createApprovedEvent(createOrganizer(), 5);
        long eventB = createApprovedEvent(createOrganizer(), 5);
        service.register(eventA, student);
        service.register(eventB, student);
        service.cancel(eventB, student);
        service.register(eventA, other);

        List<RegistrationResponse> history = service.listMine(student);

        assertThat(history).hasSize(2);
        assertThat(history).extracting(RegistrationResponse::eventId).containsExactlyInAnyOrder(eventA, eventB);
        assertThat(history).allSatisfy(item -> assertThat(item.registrationId()).isPositive());
    }

    @Test
    void rosterAllowsOwnerOrganizerAndStaffButNotOthers() {
        long owner = createOrganizer();
        long otherOrganizer = createOrganizer();
        long faculty = createFaculty();
        long admin = createAdmin();
        long student = createStudent();
        long attendee = createStudent();
        long event = createApprovedEvent(owner, 10);
        RegistrationResponse registration = service.register(event, attendee);

        EventRosterResponse roster = service.roster(event, owner);

        assertThat(roster.capacity()).isEqualTo(10);
        assertThat(roster.registeredCount()).isEqualTo(1);
        assertThat(roster.availableSlots()).isEqualTo(9);
        assertThat(roster.registrations()).hasSize(1);
        RosterEntryResponse entry = roster.registrations().get(0);
        assertThat(entry.registrationId()).isEqualTo(registration.registrationId());
        assertThat(entry.studentId()).isEqualTo(attendee);
        assertThat(entry.status()).isEqualTo(RegistrationStatus.CONFIRMED);

        assertThat(service.roster(event, faculty).eventId()).isEqualTo(event);
        assertThat(service.roster(event, admin).eventId()).isEqualTo(event);

        for (long denied : new long[] { otherOrganizer, student }) {
            ApiException exception = assertThrows(ApiException.class, () -> service.roster(event, denied));
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
