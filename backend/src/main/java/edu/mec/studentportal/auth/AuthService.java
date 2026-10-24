package edu.mec.studentportal.auth;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.security.JwtTokenService;
import edu.mec.studentportal.user.UserAccount;
import edu.mec.studentportal.user.UserAccountLookup;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final String INVALID_CREDENTIALS_MESSAGE =
        "Invalid email or password.";
    private static final String AUTHENTICATION_REQUIRED_MESSAGE =
        "Authentication is required.";

    private final UserAccountLookup userAccountLookup;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final edu.mec.studentportal.user.UserDao userDao;

    public AuthService(
        UserAccountLookup userAccountLookup,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService,
        edu.mec.studentportal.user.UserDao userDao
    ) {
        this.userAccountLookup = userAccountLookup;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.userDao = userDao;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null) {
            throw invalidCredentials();
        }

        String email = request.email();
        String password = request.password();
        if (email == null || email.isBlank()
            || password == null || password.isBlank()) {
            throw invalidCredentials();
        }

        UserAccount account = userAccountLookup.findByEmail(email)
            .orElseThrow(AuthService::invalidCredentials);

        if (!account.enabled()
            || !passwordEncoder.matches(password, account.passwordHash())) {
            throw invalidCredentials();
        }

        Jwt token = jwtTokenService.createToken(account.id(), account.role());
        UserSummaryResponse user = new UserSummaryResponse(
            account.id(),
            account.fullName(),
            account.email(),
            account.role(),
            account.mustChangePassword()
        );

        return LoginResponse.bearer(
            token.getTokenValue(),
            jwtTokenService.ttlSeconds(),
            user
        );
    }

    public UserSummaryResponse getCurrentUser(long userId) {
        if (userId <= 0) {
            throw authenticationRequired();
        }

        UserAccount account = userAccountLookup.findById(userId)
            .orElseThrow(AuthService::authenticationRequired);

        if (!account.enabled()) {
            throw authenticationRequired();
        }

        return new UserSummaryResponse(
            account.id(),
            account.fullName(),
            account.email(),
            account.role(),
            account.mustChangePassword()
        );
    }

    /**
     * Self-service password change (used by the forced first-login flow and the
     * profile screen). Requires the current password; clears the must-change
     * flag on success. There is no email-based reset — forgotten passwords are
     * reset in person by an admin.
     */
    public UserSummaryResponse changePassword(long userId,
        ChangePasswordRequest request) {
        if (userId <= 0) {
            throw authenticationRequired();
        }
        if (request == null
            || request.currentPassword() == null
            || request.currentPassword().isBlank()
            || request.newPassword() == null
            || request.newPassword().isBlank()) {
            throw badRequest(
                "currentPassword and newPassword are required."
            );
        }
        UserAccount account = userAccountLookup.findById(userId)
            .orElseThrow(AuthService::authenticationRequired);
        if (!account.enabled()) {
            throw authenticationRequired();
        }
        if (!passwordEncoder.matches(
            request.currentPassword(), account.passwordHash())) {
            throw badRequest("Current password is incorrect.");
        }
        String passwordHash = passwordEncoder.encode(
            edu.mec.studentportal.user.PasswordPolicy
                .requireNewPassword(request.newPassword()));
        if (userDao.updatePassword(account.id(), passwordHash, false) != 1) {
            throw new IllegalStateException(
                "Password change did not affect a row for user " + userId
            );
        }
        return new UserSummaryResponse(
            account.id(),
            account.fullName(),
            account.email(),
            account.role(),
            false
        );
    }

    private static ApiException badRequest(String message) {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.INVALID_REQUEST,
            message
        );
    }

    private static ApiException invalidCredentials() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCodes.UNAUTHENTICATED,
            INVALID_CREDENTIALS_MESSAGE
        );
    }

    private static ApiException authenticationRequired() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCodes.UNAUTHENTICATED,
            AUTHENTICATION_REQUIRED_MESSAGE
        );
    }
}
