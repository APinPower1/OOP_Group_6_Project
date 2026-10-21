package edu.mec.studentportal.feedback;

import edu.mec.studentportal.feedback.FeedbackResponses.FeedbackResponse;
import edu.mec.studentportal.feedback.FeedbackResponses.FeedbackSummaryResponse;
import edu.mec.studentportal.common.CurrentUser;
import jakarta.validation.Valid;
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

/** Thin controller for the feedback endpoints in docs/API_CONTRACT.md. */
@RestController
@RequestMapping("/api/events/{eventId}/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<FeedbackResponse> submit(
        @PathVariable long eventId,
        @Valid @RequestBody FeedbackRequest request,
        Authentication authentication
    ) {
        FeedbackResponse response =
            feedbackService.submit(eventId, CurrentUser.id(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<FeedbackResponse> list(@PathVariable long eventId, Authentication authentication) {
        return feedbackService.listForEvent(eventId, CurrentUser.id(authentication));
    }

    @GetMapping("/summary")
    public FeedbackSummaryResponse summary(@PathVariable long eventId, Authentication authentication) {
        return feedbackService.summary(eventId, CurrentUser.id(authentication));
    }
}
