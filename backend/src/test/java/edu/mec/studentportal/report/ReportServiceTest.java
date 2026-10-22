package edu.mec.studentportal.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.mec.studentportal.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportDAO dao;

    @InjectMocks
    private ReportService reportService;

    @Test
    void buildsReportForEnabledAdmin() {
        when(dao.findActor(9L)).thenReturn(new ReportDAO.ActorRow(9L, "ADMIN", true));
        when(dao.eventStatusCounts()).thenReturn(
            new ReportDAO.EventStatusRow(4L, 1L, 2L, 0L, 1L)
        );
        when(dao.registrationStatusCounts()).thenReturn(
            new ReportDAO.RegistrationStatusRow(10L, 7L, 3L)
        );
        when(dao.feedbackAggregate()).thenReturn(new ReportDAO.FeedbackRow(4L, 4.25));
        when(dao.participationRows(ReportService.PARTICIPATION_ROW_LIMIT)).thenReturn(
            List.of(new ParticipationReportResponse.ParticipationRow(
                5L, "Tech Fest Workshop", "APPROVED", 50, 12L, 2L, 3L, 4.33
            ))
        );

        ParticipationReportResponse report = reportService.participationReport(9L);

        assertThat(report.events().total()).isEqualTo(4L);
        assertThat(report.events().pendingApproval()).isEqualTo(1L);
        assertThat(report.events().approved()).isEqualTo(2L);
        assertThat(report.events().rejected()).isZero();
        assertThat(report.events().cancelled()).isEqualTo(1L);
        assertThat(report.registrations().total()).isEqualTo(10L);
        assertThat(report.registrations().confirmed()).isEqualTo(7L);
        assertThat(report.registrations().cancelled()).isEqualTo(3L);
        assertThat(report.feedback().count()).isEqualTo(4L);
        assertThat(report.feedback().averageRating()).isEqualTo(4.25);
        assertThat(report.participation()).hasSize(1);
        assertThat(report.participation().get(0).eventId()).isEqualTo(5L);
        assertThat(report.participation().get(0).confirmedCount()).isEqualTo(12L);
        verify(dao).participationRows(ReportService.PARTICIPATION_ROW_LIMIT);
    }

    @Test
    void nullAverageRatingPassesThroughWhenNoFeedbackExists() {
        when(dao.findActor(9L)).thenReturn(new ReportDAO.ActorRow(9L, "ADMIN", true));
        when(dao.eventStatusCounts()).thenReturn(
            new ReportDAO.EventStatusRow(0L, 0L, 0L, 0L, 0L)
        );
        when(dao.registrationStatusCounts()).thenReturn(
            new ReportDAO.RegistrationStatusRow(0L, 0L, 0L)
        );
        when(dao.feedbackAggregate()).thenReturn(new ReportDAO.FeedbackRow(0L, null));
        when(dao.participationRows(ReportService.PARTICIPATION_ROW_LIMIT))
            .thenReturn(List.of());

        ParticipationReportResponse report = reportService.participationReport(9L);

        assertThat(report.feedback().count()).isZero();
        assertThat(report.feedback().averageRating()).isNull();
        assertThat(report.participation()).isEmpty();
    }

    @Test
    void rejectsWhenActorNoLongerExists() {
        when(dao.findActor(9L)).thenReturn(null);

        assertThatThrownBy(() -> reportService.participationReport(9L))
            .isInstanceOf(ApiException.class)
            .hasMessage("Authentication is required.")
            .satisfies(exception -> {
                ApiException apiException = (ApiException) exception;
                assertThat(apiException.getStatus().value()).isEqualTo(401);
                assertThat(apiException.getCode()).isEqualTo("UNAUTHENTICATED");
            });
    }

    @Test
    void rejectsDisabledAdmin() {
        when(dao.findActor(9L)).thenReturn(new ReportDAO.ActorRow(9L, "ADMIN", false));

        assertThatThrownBy(() -> reportService.participationReport(9L))
            .isInstanceOf(ApiException.class)
            .hasMessage("Your account is disabled.")
            .satisfies(exception -> {
                ApiException apiException = (ApiException) exception;
                assertThat(apiException.getStatus().value()).isEqualTo(403);
                assertThat(apiException.getCode()).isEqualTo("FORBIDDEN");
            });
    }

    @Test
    void rejectsNonAdminActorEvenWhenSecurityChainIsBypassed() {
        when(dao.findActor(41L)).thenReturn(new ReportDAO.ActorRow(41L, "STUDENT", true));

        assertThatThrownBy(() -> reportService.participationReport(41L))
            .isInstanceOf(ApiException.class)
            .hasMessage("Administrator access is required.")
            .satisfies(exception -> assertThat(
                ((ApiException) exception).getStatus().value()
            ).isEqualTo(403));
    }
}
