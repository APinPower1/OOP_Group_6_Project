package edu.mec.studentportal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.security.JwtTokenService;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.UserAccount;
import edu.mec.studentportal.user.UserDao;
import edu.mec.studentportal.user.UserAccountLookup;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final long USER_ID = 41L;
    private static final String EMAIL = "student@example.edu";
    private static final String PASSWORD = "demo-password";
    private static final String PASSWORD_HASH =
        "$2a$10$hashThatMustNeverLeak";
    private static final long TTL_SECONDS = 900L;
    private static final String ACCESS_TOKEN = "signed-token";
    private static final String INVALID_CREDENTIALS_MESSAGE =
        "Invalid email or password.";
    private static final String AUTHENTICATION_REQUIRED_MESSAGE =
        "Authentication is required.";

    @Mock
    private UserAccountLookup userAccountLookup;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private UserDao userDao;

    @InjectMocks
    private AuthService authService;

    @Test
    void logsInWithValidCredentials() {
        stubSuccessfulLogin(account(true, Role.STUDENT), Role.STUDENT);

        LoginResponse response = authService.login(
            new LoginRequest(EMAIL, PASSWORD)
        );

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(TTL_SECONDS);
        assertThat(response.user()).isEqualTo(
            new UserSummaryResponse(
                USER_ID,
                "Demo Student",
                EMAIL,
                Role.STUDENT
            ,
                false)
        );
        verify(passwordEncoder).matches(PASSWORD, PASSWORD_HASH);
        verify(jwtTokenService).createToken(USER_ID, Role.STUDENT);
    }

    @Test
    void delegatesRawEmailToUserServiceLookup() {
        String rawEmail = "  Student@Example.EDU  ";
        when(userAccountLookup.findByEmail(rawEmail))
            .thenReturn(Optional.empty());

        assertAuthenticationFailure(catchThrowable(
            () -> authService.login(new LoginRequest(rawEmail, PASSWORD))
        ));

        verify(userAccountLookup).findByEmail(rawEmail);
        verifyNoInteractions(passwordEncoder, jwtTokenService);
    }

    @Test
    void rejectsUnknownEmailWithGenericError() {
        when(userAccountLookup.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertAuthenticationFailure(catchThrowable(
            () -> authService.login(new LoginRequest(EMAIL, PASSWORD))
        ));

        verifyNoInteractions(passwordEncoder, jwtTokenService);
    }

    @Test
    void rejectsIncorrectPasswordWithGenericError() {
        when(userAccountLookup.findByEmail(EMAIL))
            .thenReturn(Optional.of(account(true, Role.STUDENT)));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH))
            .thenReturn(false);

        assertAuthenticationFailure(catchThrowable(
            () -> authService.login(new LoginRequest(EMAIL, PASSWORD))
        ));

        verify(passwordEncoder).matches(PASSWORD, PASSWORD_HASH);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void rejectsDisabledAccountWithGenericError() {
        when(userAccountLookup.findByEmail(EMAIL))
            .thenReturn(Optional.of(account(false, Role.STUDENT)));

        assertAuthenticationFailure(catchThrowable(
            () -> authService.login(new LoginRequest(EMAIL, PASSWORD))
        ));

        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void createsTokenWithDatabaseAccountIdAndRole() {
        stubSuccessfulLogin(account(true, Role.FACULTY), Role.FACULTY);

        LoginResponse response = authService.login(
            new LoginRequest(EMAIL, PASSWORD)
        );

        verify(jwtTokenService).createToken(USER_ID, Role.FACULTY);
        assertThat(response.user().role()).isEqualTo(Role.FACULTY);
    }

    @Test
    void usesConfiguredTtlForResponseExpiry() {
        when(jwtTokenService.ttlSeconds()).thenReturn(1234L);
        stubSuccessfulLogin(
            account(true, Role.ORGANIZER),
            Role.ORGANIZER,
            false
        );

        LoginResponse response = authService.login(
            new LoginRequest(EMAIL, PASSWORD)
        );

        assertThat(response.expiresInSeconds()).isEqualTo(1234L);
    }

    @Test
    void rejectsNullRequestWithoutNullPointerException() {
        assertAuthenticationFailure(
            catchThrowable(() -> authService.login(null))
        );

        verifyNoInteractions(
            userAccountLookup,
            passwordEncoder,
            jwtTokenService
        );
    }

    @Test
    void rejectsMissingOrBlankCredentialsWithoutLookup() {
        assertAuthenticationFailure(catchThrowable(
            () -> authService.login(new LoginRequest(null, PASSWORD))
        ));
        assertAuthenticationFailure(catchThrowable(
            () -> authService.login(new LoginRequest("   ", PASSWORD))
        ));
        assertAuthenticationFailure(catchThrowable(
            () -> authService.login(new LoginRequest(EMAIL, null))
        ));
        assertAuthenticationFailure(catchThrowable(
            () -> authService.login(new LoginRequest(EMAIL, "   "))
        ));

        verifyNoInteractions(
            userAccountLookup,
            passwordEncoder,
            jwtTokenService
        );
    }

    @Test
    void returnsCurrentUserSummaryFromCurrentDatabaseAccount() {
        when(userAccountLookup.findById(USER_ID))
            .thenReturn(Optional.of(account(true, Role.FACULTY)));

        UserSummaryResponse response = authService.getCurrentUser(USER_ID);

        assertThat(response).isEqualTo(
            new UserSummaryResponse(
                USER_ID,
                "Demo Student",
                EMAIL,
                Role.FACULTY
            ,
                false)
        );
        verify(userAccountLookup).findById(USER_ID);
        verifyNoInteractions(passwordEncoder, jwtTokenService);
    }

    @Test
    void rejectsMissingCurrentUserWithAuthenticationRequired() {
        when(userAccountLookup.findById(USER_ID))
            .thenReturn(Optional.empty());

        assertAuthenticationRequired(catchThrowable(
            () -> authService.getCurrentUser(USER_ID)
        ));

        verifyNoInteractions(passwordEncoder, jwtTokenService);
    }

    @Test
    void rejectsDisabledCurrentUserWithAuthenticationRequired() {
        when(userAccountLookup.findById(USER_ID))
            .thenReturn(Optional.of(account(false, Role.STUDENT)));

        assertAuthenticationRequired(catchThrowable(
            () -> authService.getCurrentUser(USER_ID)
        ));

        verifyNoInteractions(passwordEncoder, jwtTokenService);
    }

    @Test
    void rejectsNonPositiveCurrentUserIdWithoutLookup() {
        assertAuthenticationRequired(catchThrowable(
            () -> authService.getCurrentUser(0L)
        ));
        assertAuthenticationRequired(catchThrowable(
            () -> authService.getCurrentUser(-1L)
        ));

        verifyNoInteractions(
            userAccountLookup,
            passwordEncoder,
            jwtTokenService
        );
    }

    @Test
    void currentUserResponseDoesNotContainPasswordHash() throws Exception {
        when(userAccountLookup.findById(USER_ID))
            .thenReturn(Optional.of(account(true, Role.STUDENT)));

        String json = new ObjectMapper().writeValueAsString(
            authService.getCurrentUser(USER_ID)
        );

        assertThat(json)
            .doesNotContain(PASSWORD_HASH, "passwordHash");
    }

    @Test
    void responseDoesNotContainPasswordHash() throws Exception {
        stubSuccessfulLogin(account(true, Role.STUDENT), Role.STUDENT);

        LoginResponse response = authService.login(
            new LoginRequest(EMAIL, PASSWORD)
        );
        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json)
            .doesNotContain(PASSWORD_HASH, "passwordHash");
    }

    private void stubSuccessfulLogin(UserAccount account, Role role) {
        stubSuccessfulLogin(account, role, true);
    }

    private void stubSuccessfulLogin(
        UserAccount account,
        Role role,
        boolean stubTtl
    ) {
        when(userAccountLookup.findByEmail(EMAIL))
            .thenReturn(Optional.of(account));
        when(passwordEncoder.matches(PASSWORD, account.passwordHash()))
            .thenReturn(true);
        when(jwtTokenService.createToken(account.id(), role))
            .thenReturn(token(role));
        if (stubTtl) {
            when(jwtTokenService.ttlSeconds()).thenReturn(TTL_SECONDS);
        }
    }

    @Test
    void changesPasswordWhenCurrentPasswordMatches() {
        UserAccount account = account(true, Role.STUDENT);
        when(userAccountLookup.findById(USER_ID))
            .thenReturn(Optional.of(account));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(passwordEncoder.encode("brand-new-secret-1"))
            .thenReturn("$2a$10$newhash");
        when(userDao.updatePassword(USER_ID, "$2a$10$newhash", false))
            .thenReturn(1);

        UserSummaryResponse response = authService.changePassword(
            USER_ID,
            new ChangePasswordRequest(PASSWORD, "brand-new-secret-1")
        );

        assertThat(response.mustChangePassword()).isFalse();
        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.email()).isEqualTo(EMAIL);
        verify(userDao).updatePassword(USER_ID, "$2a$10$newhash", false);
    }

    @Test
    void rejectsChangeWhenCurrentPasswordIsWrong() {
        UserAccount account = account(true, Role.STUDENT);
        when(userAccountLookup.findById(USER_ID))
            .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", PASSWORD_HASH))
            .thenReturn(false);

        Throwable thrown = catchThrowable(() -> authService.changePassword(
            USER_ID,
            new ChangePasswordRequest("wrong-password", "brand-new-secret-1")
        ));

        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException apiException = (ApiException) thrown;
        assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(apiException.getCode()).isEqualTo(ApiErrorCodes.INVALID_REQUEST);
        assertThat(apiException.getMessage()).isEqualTo("Current password is incorrect.");
        verify(userDao, never()).updatePassword(anyLong(), any(), anyBoolean());
    }

    @Test
    void rejectsChangeWithTooShortNewPassword() {
        UserAccount account = account(true, Role.STUDENT);
        when(userAccountLookup.findById(USER_ID))
            .thenReturn(Optional.of(account));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

        Throwable thrown = catchThrowable(() -> authService.changePassword(
            USER_ID,
            new ChangePasswordRequest(PASSWORD, "short")
        ));

        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException apiException = (ApiException) thrown;
        assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(apiException.getMessage())
            .isEqualTo("password must be between 8 and 72 UTF-8 bytes.");
        verify(userDao, never()).updatePassword(anyLong(), any(), anyBoolean());
    }

    @Test
    void rejectsChangeWhenAccountIsDisabled() {
        UserAccount account = account(false, Role.STUDENT);
        when(userAccountLookup.findById(USER_ID))
            .thenReturn(Optional.of(account));

        Throwable thrown = catchThrowable(() -> authService.changePassword(
            USER_ID,
            new ChangePasswordRequest(PASSWORD, "brand-new-secret-1")
        ));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getStatus())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(userDao, never()).updatePassword(anyLong(), any(), anyBoolean());
    }

    private static UserAccount account(boolean enabled, Role role) {
        return new UserAccount(
            USER_ID,
            "Demo Student",
            EMAIL,
            PASSWORD_HASH,
            "Computer Science",
            role,
            enabled
        ,
            false);
    }

    private static Jwt token(Role role) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(ACCESS_TOKEN)
            .header("alg", "HS256")
            .subject(Long.toString(USER_ID))
            .claim("role", role.name())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(TTL_SECONDS))
            .build();
    }

    private static void assertAuthenticationFailure(Throwable thrown) {
        assertThat(thrown).isInstanceOf(ApiException.class);

        ApiException exception = (ApiException) thrown;
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getCode()).isEqualTo(
            ApiErrorCodes.UNAUTHENTICATED
        );
        assertThat(exception.getMessage()).isEqualTo(
            INVALID_CREDENTIALS_MESSAGE
        );
    }

    private static void assertAuthenticationRequired(Throwable thrown) {
        assertThat(thrown).isInstanceOf(ApiException.class);

        ApiException exception = (ApiException) thrown;
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getCode()).isEqualTo(
            ApiErrorCodes.UNAUTHENTICATED
        );
        assertThat(exception.getMessage()).isEqualTo(
            AUTHENTICATION_REQUIRED_MESSAGE
        );
    }
}
