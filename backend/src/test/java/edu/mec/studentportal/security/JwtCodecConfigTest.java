package edu.mec.studentportal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class JwtCodecConfigTest {
    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(SecurityBeansConfig.class);

    @Test
    void providesJwtEncoderAndDecoderBeans() {
        validRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtEncoder.class)).isNotNull();
            assertThat(context.getBean(JwtDecoder.class)).isNotNull();
            assertThat(context.getBeansOfType(JwtEncoder.class)).hasSize(1);
            assertThat(context.getBeansOfType(JwtDecoder.class)).hasSize(1);
        });
    }

    @Test
    void encoderSignsWithHs256AndConfiguredDecoderAcceptsToken() {
        validRunner().run(context -> {
            JwtEncoder encoder = context.getBean(JwtEncoder.class);
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            Instant issuedAt = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("41")
                .claim("role", "STUDENT")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(60))
                .build();

            Jwt jwt = encoder.encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    claims
                )
            );

            assertThat(jwt.getHeaders().get("alg")).hasToString("HS256");
            assertThat(decoder.decode(jwt.getTokenValue()).getSubject())
                .isEqualTo("41");
        });
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=too-short",
                "app.jwt.ttl-seconds=900"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage(
                        "app.jwt.secret must contain at least 32 bytes"
                    );
            });
    }

    @Test
    void rejectsNonPositiveTtl() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=" + VALID_SECRET,
                "app.jwt.ttl-seconds=0"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage(
                        "app.jwt.ttl-seconds must be greater than zero"
                    );
            });
    }

    private ApplicationContextRunner validRunner() {
        return contextRunner.withPropertyValues(
            "app.jwt.secret=" + VALID_SECRET,
            "app.jwt.ttl-seconds=900"
        );
    }
}
