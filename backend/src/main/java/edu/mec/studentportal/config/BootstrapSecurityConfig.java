package edu.mec.studentportal.config;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiErrorResponseWriter;
import edu.mec.studentportal.security.JwtAuthenticationFilter;
import edu.mec.studentportal.security.JwtTokenService;
import edu.mec.studentportal.user.UserAccountLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security policy: health and login are public, admin routes require
 * the ADMIN role, and every other route requires a valid bearer JWT.
 */
@Configuration
public class BootstrapSecurityConfig {
    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(
        JwtTokenService jwtTokenService,
        UserAccountLookup userAccountLookup,
        ApiErrorResponseWriter errorWriter
    ) {
        return new JwtAuthenticationFilter(
            jwtTokenService,
            userAccountLookup,
            errorWriter
        );
    }

    @Bean
    SecurityFilterChain bootstrapSecurityFilterChain(
        HttpSecurity http,
        ApiErrorResponseWriter errorWriter,
        JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
            errorWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED.value(),
                ApiErrorCodes.UNAUTHENTICATED,
                "Authentication is required."
            );
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
            errorWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN.value(),
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to access this resource."
            );

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS
            ))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }
}