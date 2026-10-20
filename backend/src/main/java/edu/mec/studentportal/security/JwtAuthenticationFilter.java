package edu.mec.studentportal.security;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiErrorResponseWriter;
import edu.mec.studentportal.user.UserAccountLookup;
import edu.mec.studentportal.user.UserSecurityState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(
        JwtAuthenticationFilter.class
    );
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHENTICATED_MESSAGE =
        "Authentication is required.";
    private static final String INTERNAL_ERROR_MESSAGE =
        "An unexpected server error occurred.";

    private final JwtTokenService jwtTokenService;
    private final UserAccountLookup userAccountLookup;
    private final ApiErrorResponseWriter errorWriter;

    public JwtAuthenticationFilter(
        JwtTokenService jwtTokenService,
        UserAccountLookup userAccountLookup,
        ApiErrorResponseWriter errorWriter
    ) {
        this.jwtTokenService = jwtTokenService;
        this.userAccountLookup = userAccountLookup;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/health".equals(path)
            || "/api/auth/login".equals(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null
            || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        UserSecurityState state;
        try {
            Jwt jwt = jwtTokenService.decodeToken(token);
            long userId = parseUserId(jwt.getSubject());
            state = userAccountLookup.findSecurityStateById(userId)
                .orElse(null);
            if (state == null || !state.enabled()) {
                writeUnauthenticated(request, response);
                return;
            }
        } catch (JwtException | IllegalArgumentException exception) {
            writeUnauthenticated(request, response);
            return;
        } catch (DataAccessException exception) {
            log.error(
                "Failed to load user security state during authentication",
                exception
            );
            errorWriter.write(
                request,
                response,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ApiErrorCodes.INTERNAL_ERROR,
                INTERNAL_ERROR_MESSAGE
            );
            return;
        }

        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + state.role().name())
        );
        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                state,
                null,
                authorities
            );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private static long parseUserId(String subject) {
        if (subject == null) {
            throw new IllegalArgumentException("JWT subject is missing");
        }
        long userId = Long.parseLong(subject);
        if (userId <= 0) {
            throw new IllegalArgumentException("JWT subject is not a valid ID");
        }
        return userId;
    }

    private void writeUnauthenticated(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        errorWriter.write(
            request,
            response,
            HttpStatus.UNAUTHORIZED.value(),
            ApiErrorCodes.UNAUTHENTICATED,
            UNAUTHENTICATED_MESSAGE
        );
    }
}
