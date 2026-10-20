package edu.mec.studentportal.registration;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the registration/cancellation business transaction.
 *
 * <p>{@link #register(long, long)} runs in ONE service-level transaction: it locks the event row
 * with {@code SELECT ... FOR UPDATE} on the same connection as every later query, then validates
 * event state and the student role, counts only CONFIRMED rows against capacity, and inserts or
 * reactivates the registration. The database unique constraints on (student_id, event_id) and
 * ticket_no remain the last line of defence; a racing writer that still hits the unique constraint
 * is mapped to the stable 409 DUPLICATE_REGISTRATION error.
 */
@Service
public class RegistrationService {
    /** Campus-local time convention (docs/LOCAL_DEVELOPMENT.md). */
    static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");

    private final RegistrationDAO dao;
    private final Clock clock;

    @Autowired
    RegistrationService(RegistrationDAO dao) {
        this(dao, Clock.system(CAMPUS_ZONE));
    }

    RegistrationService(RegistrationDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /**
     * Registers the student for the event, or reactivates their cancelled registration if a seat
     * is still available. Returns the confirmed registration with calculated seat counts.
     */
    @Transactional
    public RegistrationResponse register(long eventId, long studentId) {
        RegistrationDAO.EventLockRow event = lockOpenEvent(eventId);
        validateEventOpenForRegistration(event);
        requireEnabledStudent(studentId);

        Optional<Registration> existing = dao.findByStudentAndEvent(studentId, eventId);
        if (existing.isPresent() && existing.get().status() == RegistrationStatus.CONFIRMED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                RegistrationErrorCodes.DUPLICATE_REGISTRATION,
                "You already hold a confirmed registration for this event."
            );
        }

        int confirmedCount = dao.countConfirmed(eventId);
        if (confirmedCount >= event.capacity()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                RegistrationErrorCodes.EVENT_FULL,
                "No seats are available for this event."
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String ticketNo = newTicketNo(eventId);
        Registration written;
        try {
            if (existing.isPresent()) {
                dao.reactivate(existing.get().id(), ticketNo, now);
                written = new Registration(
                    existing.get().id(),
                    studentId,
                    eventId,
                    now,
                    RegistrationStatus.CONFIRMED,
                    ticketNo
                );
            } else {
                long registrationId = dao.insert(studentId, eventId, ticketNo, now);
                written = new Registration(registrationId, studentId, eventId, now, RegistrationStatus.CONFIRMED, ticketNo);
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                RegistrationErrorCodes.DUPLICATE_REGISTRATION,
                "You already hold a confirmed registration for this event."
            );
        }

        int registeredCount = confirmedCount + 1;
        return toResponse(written, registeredCount, event.capacity() - registeredCount);
    }

    /**
     * Cancels the student's own confirmed registration before event start. Status becomes
     * CANCELLED; the row is never hard-deleted, and the freed seat becomes available again.
     */
    @Transactional
    public RegistrationResponse cancel(long eventId, long studentId) {
        RegistrationDAO.EventLockRow event = lockOpenEvent(eventId);

        Registration registration = dao.findByStudentAndEvent(studentId, eventId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                RegistrationErrorCodes.REGISTRATION_NOT_FOUND,
                "You have no registration for this event."
            )
        );
        if (registration.status() == RegistrationStatus.CANCELLED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                RegistrationErrorCodes.REGISTRATION_ALREADY_CANCELLED,
                "This registration is already cancelled."
            );
        }
        if (!event.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                RegistrationErrorCodes.REGISTRATION_CLOSED,
                "Registration for this event has closed."
            );
        }

        dao.updateStatus(registration.id(), RegistrationStatus.CANCELLED);
        Registration cancelled = new Registration(
            registration.id(),
            registration.studentId(),
            registration.eventId(),
            registration.registeredAt(),
            RegistrationStatus.CANCELLED,
            registration.ticketNo()
        );
        int registeredCount = dao.countConfirmed(eventId);
        return toResponse(cancelled, registeredCount, event.capacity() - registeredCount);
    }

    /** Current student's registration history, including cancelled rows. */
    @Transactional(readOnly = true)
    public List<RegistrationResponse> listMine(long studentId) {
        requireEnabledStudent(studentId);
        return dao.findByStudent(studentId).stream()
            .map(registration -> toResponse(registration, null, null))
            .toList();
    }

    /** Registration roster with minimal student data; owner organizer or Faculty/Admin only. */
    @Transactional(readOnly = true)
    public EventRosterResponse roster(long eventId, long requesterId) {
        RegistrationDAO.EventLockRow event = dao.findEvent(eventId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                RegistrationErrorCodes.EVENT_NOT_FOUND,
                "The event does not exist."
            )
        );
        requireRosterAccess(event, requesterId);
        int confirmedCount = dao.countConfirmed(eventId);

        List<RosterEntryResponse> entries = dao.findRoster(eventId).stream()
            .map(row -> new RosterEntryResponse(
                row.registrationId(),
                row.studentId(),
                row.fullName(),
                row.rollNumber(),
                row.status(),
                row.ticketNo(),
                row.registeredAt()
            ))
            .toList();
        return new EventRosterResponse(
            eventId,
            event.capacity(),
            confirmedCount,
            event.capacity() - confirmedCount,
            entries
        );
    }

    /**
     * Event-cancellation contract for the event module: cancels every CONFIRMED registration for
     * the event. Call it from the event module's own {@code @Transactional} service method so the
     * event status change, registration cancellation and notifications commit on one connection
     * (default propagation REQUIRED joins the caller's transaction).
     */
    @Transactional
    public int cancelAllConfirmedForEvent(long eventId) {
        dao.lockEvent(eventId);
        return dao.cancelConfirmedForEvent(eventId);
    }

    private RegistrationDAO.EventLockRow lockOpenEvent(long eventId) {
        return dao.lockEvent(eventId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                RegistrationErrorCodes.EVENT_NOT_FOUND,
                "The event does not exist."
            )
        );
    }

    private void requireEnabledStudent(long userId) {
        RegistrationDAO.UserRoleRow user = dao.findUser(userId).orElseThrow(() ->
            new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "Only enabled student accounts may manage registrations."
            )
        );
        if (!RegistrationDAO.ROLE_STUDENT.equals(user.role()) || !user.enabled()) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "Only enabled student accounts may manage registrations."
            );
        }
    }

    private void requireRosterAccess(RegistrationDAO.EventLockRow event, long requesterId) {
        RegistrationDAO.UserRoleRow user = dao.findUser(requesterId).orElseThrow(() ->
            new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to view this roster."
            )
        );
        boolean permitted = switch (user.role()) {
            case RegistrationDAO.ROLE_ADMIN, RegistrationDAO.ROLE_FACULTY -> user.enabled();
            case RegistrationDAO.ROLE_ORGANIZER -> user.enabled() && event.createdBy() == requesterId;
            default -> false;
        };
        if (!permitted) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to view this roster."
            );
        }
    }

    private static String newTicketNo(long eventId) {
        String random = UUID.randomUUID().toString().replace("-", "");
        return "EVT-" + eventId + "-" + random;
    }

    private static RegistrationResponse toResponse(
        Registration registration,
        Integer registeredCount,
        Integer availableSlots
    ) {
        return new RegistrationResponse(
            registration.id(),
            registration.eventId(),
            registration.status(),
            registration.ticketNo(),
            registration.registeredAt(),
            registeredCount,
            availableSlots
        );
    }

    private void validateEventOpenForRegistration(RegistrationDAO.EventLockRow event) {
        if (!RegistrationDAO.EVENT_STATUS_APPROVED.equals(event.status())) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                RegistrationErrorCodes.EVENT_NOT_APPROVED,
                "Registration is open only for approved events."
            );
        }
        if (!event.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                RegistrationErrorCodes.REGISTRATION_CLOSED,
                "Registration for this event has closed."
            );
        }
    }
}
