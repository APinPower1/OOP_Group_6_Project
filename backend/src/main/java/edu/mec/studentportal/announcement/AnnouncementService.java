package edu.mec.studentportal.announcement;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.notification.NotificationService;
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
 * Owns the announcement use cases. Publishing persists the announcement and fans out in-app
 * notifications to matching enabled users in ONE transaction via {@link NotificationService},
 * so a fan-out failure rolls the announcement back with it. Archiving retains notification
 * history; announcements are never hard-deleted.
 */
@Service
public class AnnouncementService {
    static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_ORGANIZER = "ORGANIZER";
    private static final String ROLE_FACULTY = "FACULTY";
    private static final String ROLE_ADMIN = "ADMIN";

    private final AnnouncementDAO announcementDao;
    private final NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public AnnouncementService(AnnouncementDAO announcementDao, NotificationService notificationService) {
        this(announcementDao, notificationService, Clock.system(CAMPUS_ZONE));
    }

    AnnouncementService(AnnouncementDAO announcementDao, NotificationService notificationService, Clock clock) {
        this.announcementDao = announcementDao;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /** Organizer/Faculty/Admin publishes an announcement and notifies the audience in one transaction. */
    @Transactional
    public PublishResult publish(AnnouncementRequest request, long actingUserId) {
        requirePublisher(actingUserId);
        validateAudience(request.audienceRole());
        LocalDateTime now = LocalDateTime.now(clock);
        long announcementId = announcementDao.insert(
            request.title(),
            request.content(),
            actingUserId,
            request.audienceRole(),
            now
        );
        Announcement announcement = announcementDao.findById(announcementId).orElseThrow(() ->
            new IllegalStateException("Published announcement could not be reloaded")
        );
        int notified = notificationService.notifyAnnouncement(
            announcementId,
            request.audienceRole(),
            announcement.notificationTitle(),
            announcement.notificationMessage()
        );
        return new PublishResult(AnnouncementResponse.from(announcement), notified);
    }

    /** Signed-in listing: students see matching published announcements, staff see theirs too. */
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listVisibleTo(long userId) {
        AnnouncementDAO.Actor actor = requireActor(userId);
        List<Announcement> announcements = switch (actor.role()) {
            case ROLE_STUDENT -> announcementDao.findVisibleToRole(ROLE_STUDENT);
            case ROLE_ORGANIZER, ROLE_FACULTY, ROLE_ADMIN -> announcementDao.findForStaff(userId);
            default -> List.of();
        };
        return announcements.stream().map(AnnouncementResponse::from).toList();
    }

    /** Creator Organizer/Faculty or Admin archives an announcement; history and notifications remain. */
    @Transactional
    public AnnouncementResponse archive(long announcementId, long actingUserId) {
        AnnouncementDAO.Actor actor = requireActor(actingUserId);
        Announcement announcement = announcementDao.findById(announcementId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                AnnouncementErrorCodes.ANNOUNCEMENT_NOT_FOUND,
                "The announcement does not exist."
            )
        );
        boolean permitted = ROLE_ADMIN.equals(actor.role())
            || (isPublisherRole(actor.role()) && announcement.createdBy() == actingUserId);
        if (!permitted) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to archive this announcement."
            );
        }
        if (announcement.status() != AnnouncementStatus.ARCHIVED) {
            announcementDao.archive(announcementId, LocalDateTime.now(clock));
        }
        return announcementDao.findById(announcementId).map(AnnouncementResponse::from).orElseThrow();
    }

    private void requirePublisher(long userId) {
        AnnouncementDAO.Actor actor = requireActor(userId);
        if (!isPublisherRole(actor.role())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "Only organizers, faculty and admins may publish announcements."
            );
        }
    }

    private static boolean isPublisherRole(String role) {
        return ROLE_ORGANIZER.equals(role) || ROLE_FACULTY.equals(role) || ROLE_ADMIN.equals(role);
    }

    private AnnouncementDAO.Actor requireActor(long userId) {
        AnnouncementDAO.Actor actor = announcementDao.findActor(userId).orElseThrow(() ->
            new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to use announcements."
            )
        );
        if (!actor.enabled()) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to use announcements."
            );
        }
        return actor;
    }

    private static void validateAudience(String audienceRole) {
        if (audienceRole == null) {
            return;
        }
        if (!audienceRole.equals(ROLE_STUDENT)
            && !audienceRole.equals(ROLE_ORGANIZER)
            && !audienceRole.equals(ROLE_FACULTY)
            && !audienceRole.equals(ROLE_ADMIN)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCodes.INVALID_REQUEST,
                "audienceRole must be one of STUDENT, ORGANIZER, FACULTY, ADMIN or null."
            );
        }
    }

    /** Publish result carrying the created announcement and the notification fan-out count. */
    public record PublishResult(AnnouncementResponse announcement, int notificationsCreated) {}
}
