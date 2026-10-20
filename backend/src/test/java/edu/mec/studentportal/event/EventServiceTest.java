package edu.mec.studentportal.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/** Event business rules against a real MySQL database. */
@EnabledIf(
    value = "edu.mec.studentportal.event.EventTestDatabase#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class EventServiceTest extends EventIntegrationTestSupport {
    @Autowired
    EventService service;

    private EventRequest request(String venue, LocalDateTime start, LocalDateTime end, int capacity) {
        return new EventRequest("Open Source Day", "Talks and workshops", venue, start, end, capacity);
    }

    @Test
    void organizerCreatesProposalAsPendingApproval() {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(7);

        EventResponse response = service.create(request("Auditorium A", start, start.plusHours(3), 100), organizer);

        assertThat(response.id()).isPositive();
        assertThat(response.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
        assertThat(response.createdBy()).isEqualTo(organizer);
        assertThat(response.reviewedBy()).isNull();
        assertThat(response.reviewReason()).isNull();
        assertThat(response.registeredCount()).isZero();
        assertThat(response.availableSlots()).isEqualTo(100);
        assertEventStatus(response.id(), "PENDING_APPROVAL");
    }

    @Test
    void studentCannotCreateEvent() {
        long student = createStudent();
        LocalDateTime start = now().plusDays(7);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.create(request("Auditorium A", start, start.plusHours(3), 100), student)
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getCode()).isEqualTo(ApiErrorCodes.FORBIDDEN);
    }

    @Test
    void createRejectsEndBeforeStart() {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(7);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.create(request("Auditorium A", start.plusHours(3), start, 100), organizer)
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getCode()).isEqualTo(ApiErrorCodes.INVALID_REQUEST);
    }

    @Test
    void createRejectsZeroCapacity() {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(7);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.create(request("Auditorium A", start, start.plusHours(3), 0), organizer)
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getCode()).isEqualTo(ApiErrorCodes.INVALID_REQUEST);
    }

    @Test
    void createRejectsOverlappingVenueTime() {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(7);
        insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium A", start, start.plusHours(3), 50);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.create(request("Auditorium A", start.plusHours(1), start.plusHours(4), 30), organizer)
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(EventErrorCodes.VENUE_CONFLICT);
    }

    @Test
    void createAllowsDifferentVenueOrNonOverlappingTime() {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(7);
        insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium A", start, start.plusHours(3), 50);

        EventResponse differentVenue = service.create(
            request("Seminar Hall B", start.plusHours(1), start.plusHours(4), 30), organizer);
        EventResponse nonOverlapping = service.create(
            request("Auditorium A", start.plusHours(3), start.plusHours(6), 30), organizer);

        assertThat(differentVenue.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
        assertThat(nonOverlapping.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
    }

    @Test
    void venueNormalizationIgnoresCaseAndSurroundingWhitespace() {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(7);
        insertEventRow(organizer, "PENDING_APPROVAL", " auditorium a ", start, start.plusHours(3), 50);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.create(request("AUDITORIUM A", start.plusHours(1), start.plusHours(4), 30), organizer)
        );

        assertThat(exception.getCode()).isEqualTo(EventErrorCodes.VENUE_CONFLICT);
    }

    @Test
    void backToBackEventsInSameVenueDoNotClash() {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(7);
        insertEventRow(organizer, "APPROVED", "Auditorium A", start, start.plusHours(3), 50);

        EventResponse adjacent = service.create(
            request("Auditorium A", start.plusHours(3), start.plusHours(6), 30), organizer);

        assertThat(adjacent.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
    }

    @Test
    void rejectedAndCancelledEventsDoNotBlockVenue() {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(7);
        insertEventRow(organizer, "REJECTED", "Auditorium A", start, start.plusHours(3), 50);
        insertEventRow(organizer, "CANCELLED", "Auditorium B", start, start.plusHours(3), 50);

        EventResponse afterRejected = service.create(
            request("Auditorium A", start.plusHours(1), start.plusHours(4), 30), organizer);
        EventResponse afterCancelled = service.create(
            request("Auditorium B", start.plusHours(1), start.plusHours(4), 30), organizer);

        assertThat(afterRejected.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
        assertThat(afterCancelled.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
    }

    @Test
    void overlappingBoundaryCountsAsClash() {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(7);
        insertEventRow(organizer, "APPROVED", "Auditorium A", start, start.plusHours(3), 50);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.create(
                request("Auditorium A", start.plusHours(2), start.plusHours(4), 30), organizer)
        );

        assertThat(exception.getCode()).isEqualTo(EventErrorCodes.VENUE_CONFLICT);
    }

    @Test
    void ownerEditResubmitsToPendingAndClearsReviewMetadata() {
        long organizer = createOrganizer();
        long faculty = createFaculty();
        long eventId = insertEventRow(organizer, "REJECTED", "Auditorium A", now().plusDays(7), 50);
        jdbcTemplate.update(
            "UPDATE events SET reviewed_by = ?, review_reason = ? WHERE id = ?",
            faculty, "Not this week", eventId);

        EventResponse updated = service.update(
            eventId,
            request("Seminar Hall B", now().plusDays(8), now().plusDays(8).plusHours(2), 60),
            organizer
        );

        assertThat(updated.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
        assertThat(updated.reviewedBy()).isNull();
        assertThat(updated.reviewReason()).isNull();
        assertThat(updated.venue()).isEqualTo("Seminar Hall B");
        assertEventStatus(eventId, "PENDING_APPROVAL");
    }

    @Test
    void organizerCannotEditAnotherUsersEvent() {
        long owner = createOrganizer();
        long other = createOrganizer();
        long eventId = insertEventRow(owner, "PENDING_APPROVAL", now().plusDays(7), 50);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.update(
                eventId,
                request("Seminar Hall B", now().plusDays(8), now().plusDays(8).plusHours(2), 60),
                other
            )
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void approvedOrCancelledEventsCannotBeEdited() {
        long organizer = createOrganizer();
        long approved = insertEventRow(organizer, "APPROVED", now().plusDays(7), 50);
        long cancelled = insertEventRow(organizer, "CANCELLED", now().plusDays(7), 50);
        EventRequest edit = request("Seminar Hall B", now().plusDays(8), now().plusDays(8).plusHours(2), 60);

        for (long eventId : new long[] { approved, cancelled }) {
            ApiException exception = assertThrows(
                ApiException.class,
                () -> service.update(eventId, edit, organizer)
            );
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode()).isEqualTo(EventErrorCodes.EVENT_NOT_EDITABLE);
        }
    }

    @Test
    void capacityCannotDropBelowConfirmedCountButCanIncrease() {
        long organizer = createOrganizer();
        long eventId = insertEventRow(organizer, "REJECTED", now().plusDays(7), 5);
        insertRegistrationRow(createStudent(), eventId, "CONFIRMED");
        insertRegistrationRow(createStudent(), eventId, "CONFIRMED");

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.update(
                eventId,
                request("Auditorium A", now().plusDays(7), now().plusDays(7).plusHours(3), 1),
                organizer
            )
        );
        assertThat(exception.getCode()).isEqualTo(EventErrorCodes.CAPACITY_BELOW_CONFIRMED);

        EventResponse increased = service.update(
            eventId,
            request("Auditorium A", now().plusDays(7), now().plusDays(7).plusHours(3), 10),
            organizer
        );
        assertThat(increased.capacity()).isEqualTo(10);
        assertThat(increased.registeredCount()).isEqualTo(2);
        assertThat(increased.availableSlots()).isEqualTo(8);
    }

    @Test
    void facultyAndAdminCanApprovePendingEvent() {
        long organizer = createOrganizer();
        long faculty = createFaculty();
        long admin = createAdmin();
        long eventId = insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium A", now().plusDays(7), 50);

        EventResponse approved = service.approve(eventId, faculty);

        assertThat(approved.status()).isEqualTo(EventStatus.APPROVED);
        assertThat(approved.reviewedBy()).isEqualTo(faculty);
        assertEventStatus(eventId, "APPROVED");

        long second = insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium B", now().plusDays(9), 50);
        EventResponse approvedByAdmin = service.approve(second, admin);
        assertThat(approvedByAdmin.status()).isEqualTo(EventStatus.APPROVED);
        assertThat(approvedByAdmin.reviewedBy()).isEqualTo(admin);
    }

    @Test
    void organizerCannotApprove() {
        long organizer = createOrganizer();
        long eventId = insertEventRow(organizer, "PENDING_APPROVAL", now().plusDays(7), 50);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.approve(eventId, organizer)
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void approveRejectsNonPendingStates() {
        long organizer = createOrganizer();
        long faculty = createFaculty();
        long approved = insertEventRow(organizer, "APPROVED", now().plusDays(7), 50);
        long rejected = insertEventRow(organizer, "REJECTED", now().plusDays(7), 50);
        long cancelled = insertEventRow(organizer, "CANCELLED", now().plusDays(7), 50);

        for (long eventId : new long[] { approved, rejected, cancelled }) {
            ApiException exception = assertThrows(
                ApiException.class,
                () -> service.approve(eventId, faculty)
            );
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode()).isEqualTo(EventErrorCodes.INVALID_EVENT_STATE);
        }
    }

    @Test
    void approvalRecheckCatchesClashCreatedAfterSubmission() {
        long organizer = createOrganizer();
        long otherOrganizer = createOrganizer();
        long faculty = createFaculty();
        long proposal = insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium A", now().plusDays(7), 50);
        // Second proposal slips in with the same venue/time (e.g. via direct insert or race).
        insertEventRow(otherOrganizer, "PENDING_APPROVAL", "Auditorium A", now().plusDays(7), 50);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.approve(proposal, faculty)
        );

        assertThat(exception.getCode()).isEqualTo(EventErrorCodes.VENUE_CONFLICT);
        assertEventStatus(proposal, "PENDING_APPROVAL");
    }

    @Test
    void rejectRequiresReasonAndSetsReviewMetadata() {
        long organizer = createOrganizer();
        long faculty = createFaculty();
        long withReason = insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium A", now().plusDays(7), 50);
        long withoutReason = insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium B", now().plusDays(9), 50);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.reject(withoutReason, "   ", faculty)
        );
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        EventResponse rejected = service.reject(withReason, "Venue double-booked", faculty);
        assertThat(rejected.status()).isEqualTo(EventStatus.REJECTED);
        assertThat(rejected.reviewedBy()).isEqualTo(faculty);
        assertThat(rejected.reviewReason()).isEqualTo("Venue double-booked");
        assertEventStatus(withReason, "REJECTED");
    }

    @Test
    void studentSeesOnlyApprovedEventsOrganizerSeesOwnPlusApprovedFacultySeesAll() {
        long organizer = createOrganizer();
        long otherOrganizer = createOrganizer();
        long student = createStudent();
        long faculty = createFaculty();
        long own = insertEventRow(organizer, "PENDING_APPROVAL", now().plusDays(7), 50);
        long approved = insertEventRow(otherOrganizer, "APPROVED", now().plusDays(8), 50);
        long rejected = insertEventRow(otherOrganizer, "REJECTED", now().plusDays(9), 50);

        List<EventResponse> studentList = service.list(student, null, null, null, null);
        assertThat(studentList).extracting(EventResponse::id).containsExactly(approved);

        List<EventResponse> organizerList = service.list(organizer, null, null, null, null);
        assertThat(organizerList).extracting(EventResponse::id).containsExactlyInAnyOrder(own, approved);

        List<EventResponse> facultyList = service.list(faculty, null, null, null, null);
        assertThat(facultyList).extracting(EventResponse::id).containsExactlyInAnyOrder(own, approved, rejected);
    }

    @Test
    void facultyStatusFilterAndQueryFilterNarrowResults() {
        long organizer = createOrganizer();
        long faculty = createFaculty();
        long pending = insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium A", now().plusDays(7), 50);
        insertEventRow(organizer, "APPROVED", "Seminar Hall B", now().plusDays(8), 50);

        List<EventResponse> pendingOnly = service.list(faculty, null, null, null, "PENDING_APPROVAL");
        assertThat(pendingOnly).extracting(EventResponse::id).containsExactly(pending);

        List<EventResponse> venueMatch = service.list(faculty, "seminar", null, null, null);
        assertThat(venueMatch).hasSize(1);
        assertThat(venueMatch.get(0).venue()).isEqualTo("Seminar Hall B");

        ApiException badStatus = assertThrows(
            ApiException.class,
            () -> service.list(faculty, null, null, null, "BOGUS")
        );
        assertThat(badStatus.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void studentCannotSeeHiddenEventDetails() {
        long organizer = createOrganizer();
        long student = createStudent();
        long pending = insertEventRow(organizer, "PENDING_APPROVAL", now().plusDays(7), 50);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.get(pending, student)
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo(EventErrorCodes.EVENT_NOT_FOUND);
    }

    @Test
    void ownerCancelCancelsRegistrationsAndNotifiesInOneTransaction() {
        long organizer = createOrganizer();
        long studentA = createStudent();
        long studentB = createStudent();
        long eventId = insertEventRow(organizer, "APPROVED", "Auditorium A", now().plusDays(7), 50);
        long otherEventId = insertEventRow(organizer, "APPROVED", "Auditorium B", now().plusDays(9), 50);
        insertRegistrationRow(studentA, eventId, "CONFIRMED");
        insertRegistrationRow(studentB, eventId, "CONFIRMED");
        insertRegistrationRow(studentA, otherEventId, "CONFIRMED");

        EventResponse cancelled = service.cancel(eventId, organizer);

        assertThat(cancelled.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(cancelled.registeredCount()).isZero();
        assertEventStatus(eventId, "CANCELLED");
        assertThat(confirmedCount(eventId)).isZero();
        assertThat(eventCancelledNotificationCount(eventId)).isEqualTo(2);
        assertThat(confirmedCount(otherEventId)).isEqualTo(1);
        assertThat(eventCancelledNotificationCount(otherEventId)).isZero();
    }

    @Test
    void adminCanCancelOthersEventsButOrganizerCannotAndFacultyCannot() {
        long organizer = createOrganizer();
        long otherOrganizer = createOrganizer();
        long admin = createAdmin();
        long faculty = createFaculty();
        long first = insertEventRow(organizer, "APPROVED", now().plusDays(7), 50);
        long second = insertEventRow(organizer, "PENDING_APPROVAL", now().plusDays(9), 50);

        ApiException byOtherOrganizer = assertThrows(
            ApiException.class,
            () -> service.cancel(first, otherOrganizer)
        );
        assertThat(byOtherOrganizer.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        ApiException byFaculty = assertThrows(
            ApiException.class,
            () -> service.cancel(first, faculty)
        );
        assertThat(byFaculty.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        EventResponse byAdmin = service.cancel(first, admin);
        assertThat(byAdmin.status()).isEqualTo(EventStatus.CANCELLED);

        EventResponse byOwnerPending = service.cancel(second, organizer);
        assertThat(byOwnerPending.status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void cancelRejectsRejectedAndAlreadyCancelledEvents() {
        long organizer = createOrganizer();
        long rejected = insertEventRow(organizer, "REJECTED", now().plusDays(7), 50);
        long cancelled = insertEventRow(organizer, "CANCELLED", now().plusDays(7), 50);

        for (long eventId : new long[] { rejected, cancelled }) {
            ApiException exception = assertThrows(
                ApiException.class,
                () -> service.cancel(eventId, organizer)
            );
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode()).isEqualTo(EventErrorCodes.INVALID_EVENT_STATE);
        }
    }

    @Test
    void unknownEventIdReturns404() {
        long organizer = createOrganizer();

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.get(999_999_999L, organizer)
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo(EventErrorCodes.EVENT_NOT_FOUND);
    }

    @Test
    void responseCountsComeFromConfirmedRowsOnly() {
        long organizer = createOrganizer();
        long student = createStudent();
        long eventId = insertEventRow(organizer, "APPROVED", "Auditorium A", now().plusDays(7), 20);
        insertRegistrationRow(student, eventId, "CONFIRMED");
        insertRegistrationRow(createStudent(), eventId, "CANCELLED");

        EventResponse response = service.get(eventId, organizer);

        assertThat(response.registeredCount()).isEqualTo(1);
        assertThat(response.availableSlots()).isEqualTo(19);
    }
}
