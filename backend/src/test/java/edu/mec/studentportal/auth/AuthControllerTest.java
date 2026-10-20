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
import edu.mec.studentportal.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
}
