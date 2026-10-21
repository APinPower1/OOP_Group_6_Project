package edu.mec.studentportal.security;

import edu.mec.studentportal.user.Role;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;

    public JwtTokenService(
        JwtEncoder jwtEncoder,
        JwtDecoder jwtDecoder,
        JwtProperties jwtProperties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.jwtProperties = jwtProperties;
    }

    public Jwt createToken(long userId, Role role) {
        if (userId <= 0) {
            throw new IllegalArgumentException(
                "userId must be greater than zero"
            );
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.ttlSeconds());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(Long.toString(userId))
            .claim(ROLE_CLAIM, role.name())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
    }

    public Jwt decodeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        return jwtDecoder.decode(token);
    }

    public long ttlSeconds() {
        return jwtProperties.ttlSeconds();
    }
}
