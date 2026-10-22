package edu.mec.studentportal.report;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Builds the admin participation report. Authorization is re-verified against the database
 * (role and enabled state) in addition to the security chain's ADMIN matcher, so a stale or
 * forged token cannot read reports.
 */
@Service
public class ReportService {
    static final String ADMIN_ROLE = "ADMIN";
    static final int PARTICIPATION_ROW_LIMIT = 50;

    private final ReportDAO dao;

    ReportService(ReportDAO dao) {
        this.dao = dao;
    }

    public ParticipationReportResponse participationReport(long currentUserId) {
        ReportDAO.ActorRow actor = dao.findActor(currentUserId);
        if (actor == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCodes.UNAUTHENTICATED,
                "Authentication is required."
            );
        }
        if (!actor.enabled()) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "Your account is disabled."
            );
        }
        if (!ADMIN_ROLE.equals(actor.role())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "Administrator access is required."
            );
        }

        ReportDAO.EventStatusRow events = dao.eventStatusCounts();
        ReportDAO.RegistrationStatusRow registrations = dao.registrationStatusCounts();
        ReportDAO.FeedbackRow feedback = dao.feedbackAggregate();
        List<ParticipationReportResponse.ParticipationRow> rows =
            dao.participationRows(PARTICIPATION_ROW_LIMIT);

        return new ParticipationReportResponse(
            new ParticipationReportResponse.EventCounts(
                events.total(),
                events.pendingApproval(),
                events.approved(),
                events.rejected(),
                events.cancelled()
            ),
            new ParticipationReportResponse.RegistrationCounts(
                registrations.total(),
                registrations.confirmed(),
                registrations.cancelled()
            ),
            new ParticipationReportResponse.FeedbackSummary(
                feedback.count(),
                feedback.averageRating()
            ),
            rows
        );
    }
}
