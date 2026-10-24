package edu.mec.studentportal.security;

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

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import edu.mec.studentportal.api.ApiErrorResponseWriter;
import edu.mec.studentportal.auth.AuthController;
import edu.mec.studentportal.auth.AuthService;
import edu.mec.studentportal.auth.LoginRequest;
import edu.mec.studentportal.auth.LoginResponse;
import edu.mec.studentportal.auth.UserSummaryResponse;
import edu.mec.studentportal.config.BootstrapSecurityConfig;
import edu.mec.studentportal.health.HealthController;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.UserAccountLookup;
import edu.mec.studentportal.user.UserSecurityState;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {AuthController.class, HealthController.class})
@Import({
    BootstrapSecurityConfig.class,
    SecurityBeansConfig.class,
    JwtTokenService.class,
    ApiErrorResponseWriter.class,
    JwtAuthenticationFilterTest.ProtectedTestController.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=" + JwtAuthenticationFilterTest.SECRET,
    "app.jwt.ttl-seconds=900"
})
class JwtAuthenticationFilterTest {
    static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET =
        "fedcba9876543210fedcba9876543210";
    private static final String PROTECTED_PATH = "/api/test/protected";
    private static final String ADMIN_PATH = "/api/admin/probe";
    private static final long USER_ID = 41L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private UserAccountLookup userAccountLookup;

    @MockitoBean
    private AuthService authService;

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        verifyNoInteractions(userAccountLookup);
    }

    @Test
    void loginEndpointRemainsPublic() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(
            LoginResponse.bearer(
                "signed-token",
                900L,
                new UserSummaryResponse(
                    USER_ID,
                    "Demo Student",
                    "student@example.edu",
                    Role.STUDENT
                ,
                    false)
            )
        );

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"student@example.edu","password":"demo-password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("signed-token"));

        verifyNoInteractions(userAccountLookup);
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthenticated()
        throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.path").value(PROTECTED_PATH));

        verifyNoInteractions(userAccountLookup);
    }

    @Test
    void malformedTokenReturnsUnauthenticated() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.path").value(PROTECTED_PATH));

        verifyNoInteractions(userAccountLookup);
    }

    @Test
    void tokenSignedWithDifferentKeyReturnsUnauthenticated()
        throws Exception {
        mockMvc.perform(get(PROTECTED_PATH)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + foreignToken()
                ))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.path").value(PROTECTED_PATH));

        verifyNoInteractions(userAccountLookup);
    }

    @Test
    void expiredTokenReturnsUnauthenticated() throws Exception {
        Instant issuedAt = Instant.now().minusSeconds(120);
        String token = tokenWithClaims(
            Long.toString(USER_ID),
            Role.STUDENT,
            issuedAt,
            issuedAt.plusSeconds(60)
        );

        mockMvc.perform(get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.path").value(PROTECTED_PATH));

        verifyNoInteractions(userAccountLookup);
    }

    @Test
    void invalidSubjectReturnsUnauthenticated() throws Exception {
        Instant issuedAt = Instant.now();
        String token = tokenWithClaims(
            "not-a-number",
            Role.STUDENT,
            issuedAt,
            issuedAt.plusSeconds(60)
        );

        mockMvc.perform(get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(userAccountLookup);
    }

    @Test
    void validTokenForEnabledAccountIsAuthenticated() throws Exception {
        when(userAccountLookup.findSecurityStateById(USER_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(USER_ID, Role.STUDENT, true)
            ));
        String token = jwtTokenService.createToken(
            USER_ID,
            Role.STUDENT
        ).getTokenValue();

        mockMvc.perform(get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(USER_ID))
            .andExpect(jsonPath("$.role").value("STUDENT"));

        verify(userAccountLookup).findSecurityStateById(USER_ID);
    }

    @Test
    void missingAccountReturnsUnauthenticated() throws Exception {
        when(userAccountLookup.findSecurityStateById(USER_ID))
            .thenReturn(Optional.empty());
        String token = jwtTokenService.createToken(
            USER_ID,
            Role.STUDENT
        ).getTokenValue();

        mockMvc.perform(get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.path").value(PROTECTED_PATH));
    }

    @Test
    void disabledAccountReturnsUnauthenticated() throws Exception {
        when(userAccountLookup.findSecurityStateById(USER_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(USER_ID, Role.STUDENT, false)
            ));
        String token = jwtTokenService.createToken(
            USER_ID,
            Role.STUDENT
        ).getTokenValue();

        mockMvc.perform(get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.path").value(PROTECTED_PATH));
    }

    @Test
    void currentDatabaseRoleOverridesJwtRoleClaim() throws Exception {
        when(userAccountLookup.findSecurityStateById(USER_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(USER_ID, Role.STUDENT, true)
            ));
        String token = jwtTokenService.createToken(
            USER_ID,
            Role.ADMIN
        ).getTokenValue();

        mockMvc.perform(get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void authenticatedUserWithoutRequiredAuthorityGetsForbidden()
        throws Exception {
        when(userAccountLookup.findSecurityStateById(USER_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(USER_ID, Role.STUDENT, true)
            ));
        String token = jwtTokenService.createToken(
            USER_ID,
            Role.STUDENT
        ).getTokenValue();

        mockMvc.perform(get(ADMIN_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.path").value(ADMIN_PATH));
    }

    @Test
    void staleAdminClaimCannotAccessAdminRoute() throws Exception {
        when(userAccountLookup.findSecurityStateById(USER_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(USER_ID, Role.STUDENT, true)
            ));
        String token = jwtTokenService.createToken(
            USER_ID,
            Role.ADMIN
        ).getTokenValue();

        mockMvc.perform(get(ADMIN_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void accountStateLookupFailureReturnsInternalError() throws Exception {
        when(userAccountLookup.findSecurityStateById(USER_ID))
            .thenThrow(
                new DataAccessResourceFailureException("db unavailable")
            );
        String token = jwtTokenService.createToken(
            USER_ID,
            Role.STUDENT
        ).getTokenValue();

        mockMvc.perform(get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.path").value(PROTECTED_PATH))
            .andExpect(content().string(
                not(containsString("db unavailable"))
            ));
    }

    private String tokenWithClaims(
        String subject,
        Role role,
        Instant issuedAt,
        Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(subject)
            .claim("role", role.name())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
        return jwtEncoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
            )
        ).getTokenValue();
    }

    private static String foreignToken() {
        SecretKey otherKey = new SecretKeySpec(
            OTHER_SECRET.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        JwtEncoder otherEncoder = new NimbusJwtEncoder(
            new ImmutableSecret<>(otherKey)
        );
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(Long.toString(USER_ID))
            .claim("role", Role.STUDENT.name())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(60))
            .build();
        return otherEncoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
            )
        ).getTokenValue();
    }

    @RestController
    static class ProtectedTestController {
        @GetMapping(PROTECTED_PATH)
        Map<String, Object> protectedEndpoint(Authentication authentication) {
            UserSecurityState state =
                (UserSecurityState) authentication.getPrincipal();
            return Map.of(
                "userId", state.id(),
                "role", state.role().name()
            );
        }

        @GetMapping(ADMIN_PATH)
        Map<String, String> adminEndpoint() {
            return Map.of("status", "ok");
        }
    }
}
