package edu.mec.studentportal.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {
    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SecurityBeansConfig.class)
        .withPropertyValues(
            "app.jwt.secret=" + VALID_SECRET,
            "app.jwt.ttl-seconds=1800"
        );

    @Test
    void providesBcryptPasswordEncoder() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);

            String hash = encoder.encode("demo-password");
            assertThat(hash).isNotEqualTo("demo-password");
            assertThat(hash).startsWith("$2");
            assertThat(encoder.matches("demo-password", hash)).isTrue();
            assertThat(encoder.matches("wrong-password", hash)).isFalse();
        });
    }

    @Test
    void producesDifferentHashesForTheSamePassword() {
        contextRunner.run(context -> {
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);

            String firstHash = encoder.encode("demo-password");
            String secondHash = encoder.encode("demo-password");

            assertThat(firstHash).isNotEqualTo(secondHash);
            assertThat(encoder.matches("demo-password", firstHash)).isTrue();
            assertThat(encoder.matches("demo-password", secondHash)).isTrue();
        });
    }
}
