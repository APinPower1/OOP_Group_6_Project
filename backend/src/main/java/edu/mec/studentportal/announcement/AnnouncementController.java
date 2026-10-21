package edu.mec.studentportal.announcement;

import edu.mec.studentportal.common.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

/** Thin controller for the announcement endpoints in docs/API_CONTRACT.md. */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    public ResponseEntity<AnnouncementResponse> publish(
        @Valid @RequestBody AnnouncementRequest request,
        Authentication authentication
    ) {
        AnnouncementService.PublishResult result =
            announcementService.publish(request, CurrentUser.id(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(result.announcement());
    }

    @GetMapping
    public List<AnnouncementResponse> list(Authentication authentication) {
        return announcementService.listVisibleTo(CurrentUser.id(authentication));
    }

    @PostMapping("/{id}/archive")
    public AnnouncementResponse archive(
        @PathVariable long id,
        Authentication authentication
    ) {
        return announcementService.archive(id, CurrentUser.id(authentication));
    }
}
