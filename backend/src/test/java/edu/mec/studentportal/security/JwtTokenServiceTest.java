package edu.mec.studentportal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import edu.mec.studentportal.user.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET = "fedcba9876543210fedcba9876543210";
    private static final long TTL_SECONDS = 900;

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(SecurityBeansConfig.class)
            .withPropertyValues(
                "app.jwt.secret=" + SECRET,
                "app.jwt.ttl-seconds=" + TTL_SECONDS
            );

    private JwtTokenService jwtTokenService;
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() {
        contextRunner.run(context -> {
            jwtEncoder = context.getBean(JwtEncoder.class);
            jwtTokenService = new JwtTokenService(
                jwtEncoder,
                context.getBean(JwtDecoder.class),
                context.getBean(JwtProperties.class)
            );
        });
    }

    @Test
    void createsTokenWithSubjectRoleIssuedAtAndExpiry() {
        Instant before = Instant.now();
        Jwt jwt = jwtTokenService.createToken(41L, Role.STUDENT);
        Instant after = Instant.now();

        assertThat(jwt.getSubject()).isEqualTo("41");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("STUDENT");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getIssuedAt()).isBetween(
            before.minusSeconds(1),
            after.plusSeconds(1)
        );
        assertThat(jwt.getExpiresAt()).isNotNull();
    }

    @Test
    void expiryMatchesConfiguredTtl() {
        Jwt jwt = jwtTokenService.createToken(41L, Role.STUDENT);

        assertThat(jwt.getExpiresAt())
            .isEqualTo(jwt.getIssuedAt().plusSeconds(TTL_SECONDS));
        assertThat(jwtTokenService.ttlSeconds()).isEqualTo(TTL_SECONDS);
    }

    @Test
    void decodesTokenSignedByConfiguredEncoder() {
        Jwt created = jwtTokenService.createToken(42L, Role.ADMIN);

        Jwt decoded = jwtTokenService.decodeToken(created.getTokenValue());

        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(decoded.getIssuedAt().getEpochSecond())
            .isEqualTo(created.getIssuedAt().getEpochSecond());
        assertThat(decoded.getExpiresAt().getEpochSecond())
            .isEqualTo(created.getExpiresAt().getEpochSecond());
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        SecretKey otherKey = new SecretKeySpec(
            OTHER_SECRET.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        JwtEncoder otherEncoder = new NimbusJwtEncoder(
            new ImmutableSecret<>(otherKey)
        );
        Instant issuedAt = Instant.now();
        Jwt foreignToken = otherEncoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .subject("41")
                    .claim("role", "STUDENT")
                    .issuedAt(issuedAt)
                    .expiresAt(issuedAt.plusSeconds(TTL_SECONDS))
                    .build()
            )
        );

        assertThatThrownBy(
            () -> jwtTokenService.decodeToken(foreignToken.getTokenValue())
        ).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        Instant issuedAt = Instant.now().minusSeconds(120);
        Jwt expiredToken = jwtEncoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .subject("41")
                    .claim("role", "STUDENT")
                    .issuedAt(issuedAt)
                    .expiresAt(issuedAt.plusSeconds(60))
                    .build()
            )
        );

        assertThatThrownBy(
            () -> jwtTokenService.decodeToken(expiredToken.getTokenValue())
        ).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(
            () -> jwtTokenService.decodeToken("not-a-valid-jwt")
        ).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsBlankToken() {
        assertThatThrownBy(() -> jwtTokenService.decodeToken("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("token must not be blank");
    }
}
