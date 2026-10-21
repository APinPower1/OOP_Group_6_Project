package edu.mec.studentportal.notification;

import edu.mec.studentportal.registration.RegistrationController;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin controller for the notification endpoints in docs/API_CONTRACT.md. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public List<NotificationResponse> listMine(Authentication authentication) {
        return notificationService.listMine(RegistrationController.currentUserId(authentication));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(
        @PathVariable long id,
        Authentication authentication
    ) {
        return notificationService.markRead(id, RegistrationController.currentUserId(authentication));
    }
}
