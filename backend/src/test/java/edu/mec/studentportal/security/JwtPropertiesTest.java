package edu.mec.studentportal.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtPropertiesTest {
    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsConfiguredProperties() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=" + VALID_SECRET,
                "app.jwt.ttl-seconds=900"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                JwtProperties properties = context.getBean(JwtProperties.class);
                assertThat(properties.secret()).isEqualTo(VALID_SECRET);
                assertThat(properties.ttlSeconds()).isEqualTo(900L);
            });
    }

    @Test
    void acceptsExactly32Bytes() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=" + VALID_SECRET,
                "app.jwt.ttl-seconds=1"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(JwtProperties.class).secret())
                    .isEqualTo(VALID_SECRET);
            });
    }

    @Test
    void rejectsBlankSecret() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=",
                "app.jwt.ttl-seconds=1800"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("app.jwt.secret must not be blank");
            });
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        contextRunner
            .withPropertyValues(
                "app.jwt.secret=0123456789abcdef0123456789abcde",
                "app.jwt.ttl-seconds=1800"
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

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfiguration {
    }
}
