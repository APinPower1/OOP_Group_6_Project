package edu.mec.studentportal.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long ttlSeconds) {
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.jwt.secret must not be blank");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                "app.jwt.secret must contain at least 32 bytes"
            );
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException(
                "app.jwt.ttl-seconds must be greater than zero"
            );
        }
    }
}
