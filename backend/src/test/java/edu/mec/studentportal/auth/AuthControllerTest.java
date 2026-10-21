package edu.mec.studentportal.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiErrorResponseWriter;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.config.BootstrapSecurityConfig;
import edu.mec.studentportal.health.HealthController;
import edu.mec.studentportal.security.JwtTokenService;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.UserAccountLookup;
import edu.mec.studentportal.user.UserSecurityState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, HealthController.class})
@Import({BootstrapSecurityConfig.class, ApiErrorResponseWriter.class})
class AuthControllerTest {
    private static final String LOGIN_BODY = """
        {"email":"student@example.edu","password":"demo-password"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserAccountLookup userAccountLookup;

    @Test
    void loginIsPublicAndReturnsTokenAndUserSummary() throws Exception {
        when(authService.login(
            new LoginRequest("student@example.edu", "demo-password")
        )).thenReturn(successfulResponse());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.accessToken").value("signed-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresInSeconds").value(1800))
            .andExpect(jsonPath("$.user.id").value(41))
            .andExpect(jsonPath("$.user.fullName").value("Demo Student"))
            .andExpect(jsonPath("$.user.email").value("student@example.edu"))
            .andExpect(jsonPath("$.user.role").value("STUDENT"))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andExpect(content().string(not(containsString("passwordHash"))));

        verify(authService).login(
            new LoginRequest("student@example.edu", "demo-password")
        );
    }

    @Test
    void loginIgnoresUnsupportedJsonProperties() throws Exception {
        when(authService.login(
            new LoginRequest("student@example.edu", "demo-password")
        )).thenReturn(successfulResponse());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "student@example.edu",
                      "password": "demo-password",
                      "rememberMe": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("signed-token"));

        verify(authService).login(
            new LoginRequest("student@example.edu", "demo-password")
        );
    }

    @Test
    void invalidCredentialsReturnUnauthenticatedError() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
            new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCodes.UNAUTHENTICATED,
                "Invalid email or password."
            )
        );

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"student@example.edu","password":"wrong-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.message").value(
                "Invalid email or password."
            ))
            .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"password\":\"demo-password\"}",
        "{\"email\":\"\",\"password\":\"demo-password\"}",
        "{\"email\":\"   \",\"password\":\"demo-password\"}",
        "{\"email\":\"student@example.edu\"}",
        "{\"email\":\"student@example.edu\",\"password\":\"   \"}",
        "{}"
    })
    void missingOrBlankCredentialsReturnInvalidRequest(String body)
        throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value(
                "The request is invalid or incomplete."
            ))
            .andExpect(jsonPath("$.path").value("/api/auth/login"));

        verifyNoInteractions(authService);
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.message").value(
                "Authentication is required."
            ))
            .andExpect(jsonPath("$.path").value("/api/auth/me"));

        verifyNoInteractions(
            authService,
            jwtTokenService,
            userAccountLookup
        );
    }

    @Test
    void meReturnsCurrentUserSummaryForAuthenticatedUser() throws Exception {
        when(jwtTokenService.decodeToken("valid-token"))
            .thenReturn(token(41L, Role.ADMIN));
        when(userAccountLookup.findSecurityStateById(41L))
            .thenReturn(Optional.of(
                new UserSecurityState(41L, Role.FACULTY, true)
            ));
        when(authService.getCurrentUser(41L)).thenReturn(
            new UserSummaryResponse(
                41L,
                "Demo Faculty",
                "faculty@example.edu",
                Role.FACULTY
            )
        );

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.id").value(41))
            .andExpect(jsonPath("$.fullName").value("Demo Faculty"))
            .andExpect(jsonPath("$.email").value("faculty@example.edu"))
            .andExpect(jsonPath("$.role").value("FACULTY"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(content().string(not(containsString("passwordHash"))));

        verify(authService).getCurrentUser(41L);
    }

    @Test
    void meRejectsDisabledAccount() throws Exception {
        when(jwtTokenService.decodeToken("valid-token"))
            .thenReturn(token(41L, Role.STUDENT));
        when(userAccountLookup.findSecurityStateById(41L))
            .thenReturn(Optional.of(
                new UserSecurityState(41L, Role.STUDENT, false)
            ));

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.path").value("/api/auth/me"));

        verifyNoInteractions(authService);
    }

    @Test
    void meRejectsDeletedAccount() throws Exception {
        when(jwtTokenService.decodeToken("valid-token"))
            .thenReturn(token(41L, Role.STUDENT));
        when(userAccountLookup.findSecurityStateById(41L))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.path").value("/api/auth/me"));

        verifyNoInteractions(authService);
    }

    @Test
    void meRejectsInvalidOrExpiredToken() throws Exception {
        when(jwtTokenService.decodeToken("bad-token"))
            .thenThrow(new JwtException("invalid token"));

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.path").value("/api/auth/me"));

        verifyNoInteractions(authService, userAccountLookup);
    }

    private static LoginResponse successfulResponse() {
        return LoginResponse.bearer(
            "signed-token",
            1800L,
            new UserSummaryResponse(
                41L,
                "Demo Student",
                "student@example.edu",
                Role.STUDENT
            )
        );
    }

    private static Jwt token(long userId, Role role) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue("valid-token")
            .header("alg", "HS256")
            .subject(Long.toString(userId))
            .claim("role", role.name())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(60))
            .build();
    }
}
