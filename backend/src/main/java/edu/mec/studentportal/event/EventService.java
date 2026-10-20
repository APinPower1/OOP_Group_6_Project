package edu.mec.studentportal.event;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.registration.RegistrationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns event proposal lifecycle rules: creation as PENDING_APPROVAL, owner-only edits that
 * resubmit to review, Faculty/Admin approval with a venue re-check, rejection with a reason,
 * and transactional cancellation that coordinates Member 4's registration service and the
 * EVENT_CANCELLED notification collaborator on one connection.
 */
@Service
public class EventService {
    /** Campus-local time convention (docs/LOCAL_DEVELOPMENT.md). */
    static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");

    private final EventDAO dao;
    private final RegistrationService registrationService;
    private final EventCancellationNotifier cancellationNotifier;
    private final Clock clock;

    @Autowired
    EventService(
        EventDAO dao,
        RegistrationService registrationService,
        EventCancellationNotifier cancellationNotifier
    ) {
        this(dao, registrationService, cancellationNotifier, Clock.system(CAMPUS_ZONE));
    }

    EventService(
        EventDAO dao,
        RegistrationService registrationService,
        EventCancellationNotifier cancellationNotifier,
        Clock clock
    ) {
        this.dao = dao;
        this.registrationService = registrationService;
        this.cancellationNotifier = cancellationNotifier;
        this.clock = clock;
    }

    /** Creates a proposal; status is always server-set to PENDING_APPROVAL. */
    @Transactional
    public EventResponse create(EventRequest request, long requesterId) {
        requireRole(requesterId, EventDAO.ROLE_ORGANIZER, "Only organizer accounts can create events.");
        validateRequest(request);

        LocalDateTime startTime = request.startTime().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime endTime = request.endTime().truncatedTo(ChronoUnit.SECONDS);
        String venue = request.venue().trim();
        requireNoVenueConflict(venue, startTime, endTime, null);

        long eventId = dao.insert(
            request.title().trim(),
            request.description(),
            venue,
            startTime,
            endTime,
            request.capacity(),
            requesterId
        );
        Event event = dao.findById(eventId).orElseThrow(() -> notFound());
        return toResponse(event, 0);
    }

    /**
     * Owner organizer edits a PENDING_APPROVAL/REJECTED proposal; the edit clears any prior
     * review metadata and resubmits the event as PENDING_APPROVAL. Approved events are never
     * edited — cancel and create a new proposal instead.
     */
    @Transactional
    public EventResponse update(long eventId, EventRequest request, long requesterId) {
        requireRole(requesterId, EventDAO.ROLE_ORGANIZER, "Only organizer accounts can edit events.");
        Event event = lockEvent(eventId);
        if (event.createdBy() != requesterId) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You can only edit events you created."
            );
        }
        if (event.status() != EventStatus.PENDING_APPROVAL && event.status() != EventStatus.REJECTED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                EventErrorCodes.EVENT_NOT_EDITABLE,
                "Only pending or rejected proposals can be edited. Cancel the event and create a new proposal instead."
            );
        }
        validateRequest(request);

        LocalDateTime startTime = request.startTime().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime endTime = request.endTime().truncatedTo(ChronoUnit.SECONDS);
        String venue = request.venue().trim();
        int confirmedCount = dao.countConfirmed(eventId);
        if (request.capacity() < confirmedCount) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                EventErrorCodes.CAPACITY_BELOW_CONFIRMED,
                "Capacity cannot be lower than the current confirmed registration count."
            );
        }
        requireNoVenueConflict(venue, startTime, endTime, eventId);

        dao.updateDetails(
            eventId,
            request.title().trim(),
            request.description(),
            venue,
            startTime,
            endTime,
            request.capacity()
        );
        Event updated = dao.findById(eventId).orElseThrow(() -> notFound());
        return toResponse(updated, confirmedCount);
    }

    /** Role-scoped listing; students only ever receive APPROVED events. */
    @Transactional(readOnly = true)
    public List<EventResponse> list(
        long requesterId,
        String query,
        LocalDateTime from,
        LocalDateTime to,
        String statusFilter
    ) {
        EventDAO.UserRoleRow user = requireEnabledUser(requesterId);
        EventStatus status = parseStatusFilter(statusFilter, user.role());

        List<Event> events = dao.search(requesterId, user.role(), query, from, to, status);
        Map<Long, Integer> counts = dao.countConfirmedByEventIds(
            events.stream().map(Event::id).collect(Collectors.toList())
        );
        return events.stream()
            .map(event -> toResponse(event, counts.getOrDefault(event.id(), 0)))
            .toList();
    }

    /** Event details with role-appropriate visibility; invisible events read as 404. */
    @Transactional(readOnly = true)
    public EventResponse get(long eventId, long requesterId) {
        EventDAO.UserRoleRow user = requireEnabledUser(requesterId);
        Event event = dao.findById(eventId).orElseThrow(() -> notFound());
        requireVisible(event, user);
        return toResponse(event, dao.countConfirmed(eventId));
    }

    /** Faculty/Admin approve a pending proposal after re-checking the venue clash. */
    @Transactional
    public EventResponse approve(long eventId, long reviewerId) {
        requireRole(reviewerId, EventDAO.ROLE_FACULTY, EventDAO.ROLE_ADMIN,
            "Only faculty or admin accounts can approve events.");
        Event event = lockEvent(eventId);
        if (event.status() != EventStatus.PENDING_APPROVAL) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                EventErrorCodes.INVALID_EVENT_STATE,
                "Only pending events can be approved."
            );
        }
        requireNoVenueConflict(event.venue(), event.startTime(), event.endTime(), eventId);

        dao.markApproved(eventId, reviewerId);
        Event approved = dao.findById(eventId).orElseThrow(() -> notFound());
        return toResponse(approved, dao.countConfirmed(eventId));
    }

    /** Faculty/Admin reject a pending proposal; a non-blank reason is mandatory. */
    @Transactional
    public EventResponse reject(long eventId, String reason, long reviewerId) {
        requireRole(reviewerId, EventDAO.ROLE_FACULTY, EventDAO.ROLE_ADMIN,
            "Only faculty or admin accounts can reject events.");
        if (!StringUtils.hasText(reason)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCodes.INVALID_REQUEST,
                "A rejection reason is required."
            );
        }
        Event event = lockEvent(eventId);
        if (event.status() != EventStatus.PENDING_APPROVAL) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                EventErrorCodes.INVALID_EVENT_STATE,
                "Only pending events can be rejected."
            );
        }

        dao.markRejected(eventId, reviewerId, reason.trim());
        Event rejected = dao.findById(eventId).orElseThrow(() -> notFound());
        return toResponse(rejected, dao.countConfirmed(eventId));
    }

    /**
     * Cancels a PENDING_APPROVAL or APPROVED event in ONE transaction: event status change,
     * EVENT_CANCELLED notifications for the snapshotted confirmed registrants, and Member 4's
     * registration cancellation via REQUIRED-propagation join. Owner organizer or Admin only.
     */
    @Transactional
    public EventResponse cancel(long eventId, long cancellerId) {
        EventDAO.UserRoleRow user = requireEnabledUser(cancellerId);
        Event event = lockEvent(eventId);

        boolean permitted = EventDAO.ROLE_ADMIN.equals(user.role())
            || (EventDAO.ROLE_ORGANIZER.equals(user.role()) && event.createdBy() == cancellerId);
        if (!permitted) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "Only the event creator or an admin can cancel this event."
            );
        }
        if (event.status() != EventStatus.APPROVED && event.status() != EventStatus.PENDING_APPROVAL) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                EventErrorCodes.INVALID_EVENT_STATE,
                "Only approved or pending events can be cancelled."
            );
        }

        List<Long> registrantUserIds = dao.findConfirmedRegistrantUserIds(eventId);
        dao.markCancelled(eventId);
        cancellationNotifier.notifyEventCancelled(eventId, event.title(), registrantUserIds);
        registrationService.cancelAllConfirmedForEvent(eventId);

        Event cancelled = dao.findById(eventId).orElseThrow(() -> notFound());
        return toResponse(cancelled, 0);
    }

    private Event lockEvent(long eventId) {
        return dao.lockById(eventId).orElseThrow(() -> notFound());
    }

    private EventDAO.UserRoleRow requireEnabledUser(long userId) {
        return dao.findUser(userId).orElseThrow(() -> new ApiException(
            HttpStatus.FORBIDDEN,
            ApiErrorCodes.FORBIDDEN,
            "Your account is not permitted to manage events."
        ));
    }

    private void requireRole(long userId, String requiredRole, String message) {
        requireRole(userId, requiredRole, null, message);
    }

    private void requireRole(long userId, String requiredRole, String alternateRole, String message) {
        EventDAO.UserRoleRow user = requireEnabledUser(userId);
        boolean permitted = (requiredRole.equals(user.role())
            || (alternateRole != null && alternateRole.equals(user.role())))
            && user.enabled();
        if (!permitted) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCodes.FORBIDDEN, message);
        }
    }

    private void requireVisible(Event event, EventDAO.UserRoleRow user) {
        if (!user.enabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCodes.FORBIDDEN, "Your account is not permitted to view this event.");
        }
        boolean visible = switch (user.role()) {
            case EventDAO.ROLE_FACULTY, EventDAO.ROLE_ADMIN -> true;
            case EventDAO.ROLE_ORGANIZER -> event.status() == EventStatus.APPROVED || event.createdBy() == user.id();
            case EventDAO.ROLE_STUDENT -> event.status() == EventStatus.APPROVED;
            default -> false;
        };
        if (!visible) {
            throw notFound();
        }
    }

    private void requireNoVenueConflict(String venue, LocalDateTime startTime, LocalDateTime endTime, Long excludeEventId) {
        dao.findVenueConflict(venue, startTime, endTime, excludeEventId == null ? -1L : excludeEventId)
            .ifPresent(conflicting -> {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    EventErrorCodes.VENUE_CONFLICT,
                    "Venue \"" + conflicting.venue() + "\" is already booked from "
                        + conflicting.startTime() + " to " + conflicting.endTime()
                        + " by event \"" + conflicting.title() + "\"."
                );
            });
    }

    private static void validateRequest(EventRequest request) {
        if (request.startTime() == null || request.endTime() == null
            || !request.endTime().isAfter(request.startTime())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCodes.INVALID_REQUEST,
                "End time must be after start time."
            );
        }
        if (request.capacity() <= 0) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCodes.INVALID_REQUEST,
                "Capacity must be a positive number."
            );
        }
    }

    private static EventStatus parseStatusFilter(String raw, String role) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        if (EventDAO.ROLE_STUDENT.equals(role)) {
            return EventStatus.APPROVED;
        }
        try {
            return EventStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCodes.INVALID_REQUEST,
                "Unknown event status filter."
            );
        }
    }

    private static ApiException notFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            EventErrorCodes.EVENT_NOT_FOUND,
            "The event does not exist."
        );
    }

    private static EventResponse toResponse(Event event, int registeredCount) {
        return new EventResponse(
            event.id(),
            event.title(),
            event.description(),
            event.venue(),
            event.startTime(),
            event.endTime(),
            event.capacity(),
            event.status(),
            event.createdBy(),
            event.reviewedBy(),
            event.reviewReason(),
            event.createdAt(),
            event.updatedAt(),
            registeredCount,
            event.capacity() - registeredCount
        );
    }
}
