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

    public AuthService(
        UserAccountLookup userAccountLookup,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService
    ) {
        this.userAccountLookup = userAccountLookup;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
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
            account.role()
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
            account.role()
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
