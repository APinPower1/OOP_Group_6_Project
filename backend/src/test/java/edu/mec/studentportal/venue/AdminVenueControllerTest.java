package edu.mec.studentportal.venue;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.mec.studentportal.api.ApiErrorResponseWriter;
import edu.mec.studentportal.config.BootstrapSecurityConfig;
import edu.mec.studentportal.security.JwtTokenService;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.UserAccountLookup;
import edu.mec.studentportal.user.UserSecurityState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminVenueController.class)
@Import({BootstrapSecurityConfig.class, ApiErrorResponseWriter.class})
class AdminVenueControllerTest {
    private static final String ADMIN_TOKEN = "admin-token";
    private static final long ADMIN_ID = 99L;
    private static final String STUDENT_TOKEN = "student-token";
    private static final long STUDENT_ID = 41L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserAccountLookup userAccountLookup;

    @Test
    void createsVenueThroughRealApi() throws Exception {
        authenticateAsAdmin();
        when(venueService.createVenue(new VenueCreateRequest("New Hall")))
            .thenReturn(new VenueResponse(5L, "New Hall"));

        mockMvc.perform(post("/api/admin/venues")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Hall\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.name").value("New Hall"));

        verify(venueService).createVenue(new VenueCreateRequest("New Hall"));
    }

    @Test
    void studentCannotCreateVenue() throws Exception {
        when(jwtTokenService.decodeToken(STUDENT_TOKEN))
            .thenReturn(token(STUDENT_TOKEN, STUDENT_ID, Role.STUDENT));
        when(userAccountLookup.findSecurityStateById(STUDENT_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(STUDENT_ID, Role.STUDENT, true)
            ));

        mockMvc.perform(post("/api/admin/venues")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Hall\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deletesVenueThroughRealApi() throws Exception {
        authenticateAsAdmin();
        when(venueService.deleteVenue(5L)).thenReturn(new VenueResponse(5L, null));

        mockMvc.perform(delete("/api/admin/venues/5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5));

        verify(venueService).deleteVenue(5L);
    }

    @Test
    void rejectsNonNumericVenueId() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(delete("/api/admin/venues/not-a-number")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    private void authenticateAsAdmin() {
        when(jwtTokenService.decodeToken(ADMIN_TOKEN))
            .thenReturn(token(ADMIN_TOKEN, ADMIN_ID, Role.ADMIN));
        when(userAccountLookup.findSecurityStateById(ADMIN_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(ADMIN_ID, Role.ADMIN, true)
            ));
    }

    private static Jwt token(String tokenValue, long userId, Role role) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(tokenValue)
            .header("alg", "HS256")
            .subject(Long.toString(userId))
            .claim("role", role.name())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(60))
            .build();
    }
}
