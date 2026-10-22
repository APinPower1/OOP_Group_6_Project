package edu.mec.studentportal.event;

import edu.mec.studentportal.common.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller for the event endpoints in docs/API_CONTRACT.md.
 * The acting user is always derived from the authenticated principal; request bodies never
 * carry creator, status or review metadata. Role authority stays with Spring Security plus
 * the service's database-backed checks.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) LocalDateTime from,
        @RequestParam(required = false) LocalDateTime to,
        @RequestParam(required = false) String status,
        Authentication authentication
    ) {
        return eventService.list(CurrentUser.id(authentication), q, from, to, status);
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable long eventId, Authentication authentication) {
        return eventService.get(eventId, CurrentUser.id(authentication));
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(
        @Valid @RequestBody EventRequest request,
        Authentication authentication
    ) {
        EventResponse response = eventService.create(request, CurrentUser.id(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{eventId}")
    public EventResponse update(
        @PathVariable long eventId,
        @Valid @RequestBody EventRequest request,
        Authentication authentication
    ) {
        return eventService.update(eventId, request, CurrentUser.id(authentication));
    }

    @PostMapping("/{eventId}/approve")
    public EventResponse approve(@PathVariable long eventId, Authentication authentication) {
        return eventService.approve(eventId, CurrentUser.id(authentication));
    }

    @PostMapping("/{eventId}/reject")
    public EventResponse reject(
        @PathVariable long eventId,
        @Valid @RequestBody RejectRequest request,
        Authentication authentication
    ) {
        return eventService.reject(eventId, request.reason(), CurrentUser.id(authentication));
    }

    @PostMapping("/{eventId}/cancel")
    public EventResponse cancel(@PathVariable long eventId, Authentication authentication) {
        return eventService.cancel(eventId, CurrentUser.id(authentication));
    }
}
