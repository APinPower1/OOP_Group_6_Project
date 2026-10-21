package edu.mec.studentportal.feedback;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.feedback.FeedbackResponses.FeedbackResponse;
import edu.mec.studentportal.feedback.FeedbackResponses.FeedbackSummaryResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns feedback submission and access rules. A student may submit one rating 1-5 (comment
 * optional) only after the event's end time and only with a confirmed registration; the
 * unique (student_id, event_id) constraint backs the application check. Full records are
 * visible only to the event's owner Organizer, Faculty/Admin; any signed-in viewer sees only
 * the average rating and count.
 */
@Service
public class FeedbackService {
    static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_ORGANIZER = "ORGANIZER";
    private static final String ROLE_FACULTY = "FACULTY";
    private static final String ROLE_ADMIN = "ADMIN";

    private final FeedbackDAO dao;
    private final Clock clock;

    @Autowired
    FeedbackService(FeedbackDAO dao) {
        this(dao, Clock.system(CAMPUS_ZONE));
    }

    FeedbackService(FeedbackDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    @Transactional
    public FeedbackResponse submit(long eventId, long studentId, FeedbackRequest request) {
        if (request.rating() < 1 || request.rating() > 5) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCodes.INVALID_REQUEST,
                "Rating must be an integer between 1 and 5."
            );
        }
        requireStudent(studentId);
        FeedbackDAO.EventRow event = dao.findEvent(eventId);
        if (event == null) {
            throw new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCodes.NOT_FOUND,
                "The event does not exist."
            );
        }
        if (!event.endTime().isBefore(LocalDateTime.now(clock))) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                FeedbackErrorCodes.FEEDBACK_NOT_ALLOWED,
                "Feedback is accepted only after the event has ended."
            );
        }
        if (!dao.hasConfirmedRegistration(studentId, eventId)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                FeedbackErrorCodes.FEEDBACK_NOT_ALLOWED,
                "Only students with a confirmed registration may submit feedback."
            );
        }
        if (dao.existsFeedback(studentId, eventId)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                FeedbackErrorCodes.DUPLICATE_FEEDBACK,
                "You have already submitted feedback for this event."
            );
        }

        long id;
        try {
            id = dao.insert(studentId, eventId, request.rating(), request.comments(), LocalDateTime.now(clock));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                FeedbackErrorCodes.DUPLICATE_FEEDBACK,
                "You have already submitted feedback for this event."
            );
        }
        return FeedbackResponse.from(dao.findById(id));
    }

    /** Owner Organizer, Faculty/Admin see full feedback records for the event. */
    @Transactional(readOnly = true)
    public List<FeedbackResponse> listForEvent(long eventId, long requesterId) {
        FeedbackDAO.EventRow event = requireEvent(eventId);
        requireFullAccess(event, requesterId);
        return dao.findByEvent(eventId).stream().map(FeedbackResponse::from).toList();
    }

    /** Any signed-in viewer sees only average rating and count. */
    @Transactional(readOnly = true)
    public FeedbackSummaryResponse summary(long eventId, long requesterId) {
        requireEvent(eventId);
        dao.findActor(requesterId);
        List<Integer> ratings = dao.findRatingsForEvent(eventId);
        double average = ratings.isEmpty()
            ? 0.0
            : ratings.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        return new FeedbackSummaryResponse(eventId, average, ratings.size());
    }

    private void requireStudent(long userId) {
        FeedbackDAO.ActorRow actor = dao.findActor(userId);
        if (actor == null || !actor.enabled() || !ROLE_STUDENT.equals(actor.role())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "Only enabled student accounts may submit feedback."
            );
        }
    }

    private FeedbackDAO.EventRow requireEvent(long eventId) {
        FeedbackDAO.EventRow event = dao.findEvent(eventId);
        if (event == null) {
            throw new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCodes.NOT_FOUND,
                "The event does not exist."
            );
        }
        return event;
    }

    private void requireFullAccess(FeedbackDAO.EventRow event, long requesterId) {
        FeedbackDAO.ActorRow actor = dao.findActor(requesterId);
        boolean permitted = actor != null && actor.enabled() && switch (actor.role()) {
            case ROLE_ADMIN, ROLE_FACULTY -> true;
            case ROLE_ORGANIZER -> event.createdBy() == requesterId;
            default -> false;
        };
        if (!permitted) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to view feedback for this event."
            );
        }
    }
}
