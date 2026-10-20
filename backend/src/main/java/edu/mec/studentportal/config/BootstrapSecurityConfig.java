package edu.mec.studentportal.config;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Safe bootstrap policy: health and login are public and all unfinished API routes are denied.
 * Member 2 must replace/extend this with the documented stateless JWT policy before
 * protected endpoints are exposed. Do not change this to permitAll for all routes.
 */
@Configuration
public class BootstrapSecurityConfig {
    @Bean
    SecurityFilterChain bootstrapSecurityFilterChain(
        HttpSecurity http,
        ApiErrorResponseWriter errorWriter
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
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                .anyRequest().denyAll()
            );
        return http.build();
    }
}