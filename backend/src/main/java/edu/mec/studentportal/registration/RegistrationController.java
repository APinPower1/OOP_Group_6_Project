package edu.mec.studentportal.registration;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller for the registration endpoints in docs/API_CONTRACT.md.
 * The acting student is always derived from the authenticated principal; request bodies are
 * not accepted and never carry a student id. Role authority stays with Spring Security plus
 * the service's database-backed checks.
 */
@RestController
@RequestMapping
public class RegistrationController {
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/api/events/{eventId}/registrations")
    public ResponseEntity<RegistrationResponse> register(
        @PathVariable long eventId,
        Authentication authentication
    ) {
        RegistrationResponse response = registrationService.register(eventId, currentUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/api/events/{eventId}/registrations/me")
    public RegistrationResponse cancelOwn(
        @PathVariable long eventId,
        Authentication authentication
    ) {
        return registrationService.cancel(eventId, currentUserId(authentication));
    }

    @GetMapping("/api/registrations/me")
    public List<RegistrationResponse> listOwn(Authentication authentication) {
        return registrationService.listMine(currentUserId(authentication));
    }

    @GetMapping("/api/events/{eventId}/registrations")
    public EventRosterResponse roster(
        @PathVariable long eventId,
        Authentication authentication
    ) {
        return registrationService.roster(eventId, currentUserId(authentication));
    }

    /** Resolves the server-side user id from the security principal (Member 2's JWT subject). */
    private static long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCodes.UNAUTHENTICATED,
                "Authentication is required."
            );
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCodes.UNAUTHENTICATED,
                "Authentication is required."
            );
        }
    }
}
