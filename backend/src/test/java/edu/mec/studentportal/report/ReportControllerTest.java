package edu.mec.studentportal.report;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.mec.studentportal.api.ApiErrorResponseWriter;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.config.BootstrapSecurityConfig;
import edu.mec.studentportal.security.JwtTokenService;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.UserSecurityState;
import edu.mec.studentportal.user.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReportController.class)
@Import({BootstrapSecurityConfig.class, ApiErrorResponseWriter.class})
class ReportControllerTest {
    private static final String ADMIN_TOKEN = "admin-token";
    private static final long ADMIN_ID = 99L;
    private static final String STUDENT_TOKEN = "student-token";
    private static final long STUDENT_ID = 41L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    void returnsParticipationReportForAdmin() throws Exception {
        authenticateAsAdmin();
        when(reportService.participationReport(ADMIN_ID)).thenReturn(sampleReport());

        mockMvc.perform(get("/api/admin/reports/participation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events.total").value(4))
            .andExpect(jsonPath("$.events.pendingApproval").value(1))
            .andExpect(jsonPath("$.events.approved").value(2))
            .andExpect(jsonPath("$.events.rejected").value(0))
            .andExpect(jsonPath("$.events.cancelled").value(1))
            .andExpect(jsonPath("$.registrations.total").value(10))
            .andExpect(jsonPath("$.registrations.confirmed").value(7))
            .andExpect(jsonPath("$.registrations.cancelled").value(3))
            .andExpect(jsonPath("$.feedback.count").value(4))
            .andExpect(jsonPath("$.feedback.averageRating").value(4.25))
            .andExpect(jsonPath("$.participation[0].eventId").value(5))
            .andExpect(jsonPath("$.participation[0].title").value("Tech Fest Workshop"))
            .andExpect(jsonPath("$.participation[0].status").value("APPROVED"))
            .andExpect(jsonPath("$.participation[0].capacity").value(50))
            .andExpect(jsonPath("$.participation[0].confirmedCount").value(12))
            .andExpect(jsonPath("$.participation[0].feedbackCount").value(3))
            .andExpect(jsonPath("$.participation[0].averageRating").value(4.33));

        verify(reportService).participationReport(ADMIN_ID);
    }

    @Test
    void serializesNullAverageRatingAsJsonNull() throws Exception {
        authenticateAsAdmin();
        when(reportService.participationReport(ADMIN_ID)).thenReturn(
            new ParticipationReportResponse(
                new ParticipationReportResponse.EventCounts(0, 0, 0, 0, 0),
                new ParticipationReportResponse.RegistrationCounts(0, 0, 0),
                new ParticipationReportResponse.FeedbackSummary(0, null),
                List.of(new ParticipationReportResponse.ParticipationRow(
                    7L, "Empty Event", "APPROVED", 30, 0L, 0L, 0L, null
                ))
            )
        );

        mockMvc.perform(get("/api/admin/reports/participation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.feedback.averageRating").value(nullValue()))
            .andExpect(jsonPath("$.participation[0].averageRating").value(nullValue()));
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/reports/participation"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsStudentTokenAtSecurityChain() throws Exception {
        authenticateAsStudent();

        mockMvc.perform(get("/api/admin/reports/participation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
            .andExpect(status().isForbidden());
    }

    @Test
    void mapsServiceForbiddenToErrorBody() throws Exception {
        authenticateAsAdmin();
        when(reportService.participationReport(ADMIN_ID)).thenThrow(
            new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Your account is disabled.")
        );

        mockMvc.perform(get("/api/admin/reports/participation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Your account is disabled."));
    }

    private static ParticipationReportResponse sampleReport() {
        return new ParticipationReportResponse(
            new ParticipationReportResponse.EventCounts(4, 1, 2, 0, 1),
            new ParticipationReportResponse.RegistrationCounts(10, 7, 3),
            new ParticipationReportResponse.FeedbackSummary(4, 4.25),
            List.of(new ParticipationReportResponse.ParticipationRow(
                5L, "Tech Fest Workshop", "APPROVED", 50, 12L, 2L, 3L, 4.33
            ))
        );
    }

    private void authenticateAsAdmin() {
        when(jwtTokenService.decodeToken(ADMIN_TOKEN))
            .thenReturn(token(ADMIN_TOKEN, ADMIN_ID));
        when(userService.findSecurityStateById(ADMIN_ID))
            .thenReturn(Optional.of(new UserSecurityState(ADMIN_ID, Role.ADMIN, true)));
    }

    private void authenticateAsStudent() {
        when(jwtTokenService.decodeToken(STUDENT_TOKEN))
            .thenReturn(token(STUDENT_TOKEN, STUDENT_ID));
        when(userService.findSecurityStateById(STUDENT_ID))
            .thenReturn(Optional.of(new UserSecurityState(STUDENT_ID, Role.STUDENT, true)));
    }

    private static Jwt token(String value, long userId) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(value)
            .header("alg", "HS256")
            .subject(Long.toString(userId))
            .claim("role", "USER")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(60))
            .build();
    }
}
