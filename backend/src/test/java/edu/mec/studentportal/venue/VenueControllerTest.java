package edu.mec.studentportal.venue;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.mec.studentportal.api.ApiErrorResponseWriter;
import edu.mec.studentportal.config.BootstrapSecurityConfig;
import edu.mec.studentportal.security.JwtTokenService;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.UserAccountLookup;
import edu.mec.studentportal.user.UserSecurityState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = VenueController.class)
@Import({BootstrapSecurityConfig.class, ApiErrorResponseWriter.class})
class VenueControllerTest {
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
    void venueListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/venues"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void returnsVenuesForSignedInUser() throws Exception {
        when(jwtTokenService.decodeToken(STUDENT_TOKEN))
            .thenReturn(token(STUDENT_TOKEN, STUDENT_ID, Role.STUDENT));
        when(userAccountLookup.findSecurityStateById(STUDENT_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(STUDENT_ID, Role.STUDENT, true)
            ));
        when(venueService.listVenues()).thenReturn(List.of(
            new VenueResponse(1L, "Main Auditorium"),
            new VenueResponse(2L, "Seminar Hall A")
        ));

        mockMvc.perform(get("/api/venues")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Main Auditorium"))
            .andExpect(jsonPath("$[1].name").value("Seminar Hall A"));
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
