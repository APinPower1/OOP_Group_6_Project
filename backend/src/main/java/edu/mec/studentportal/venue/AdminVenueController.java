package edu.mec.studentportal.venue;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only venue management. Lives under /api/admin/** so the security
 * config restricts it to ROLE_ADMIN without extra rules.
 */
@RestController
@RequestMapping("/api/admin/venues")
public class AdminVenueController {
    private final VenueService venueService;

    public AdminVenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse createVenue(@RequestBody VenueCreateRequest request) {
        return venueService.createVenue(request);
    }

    @DeleteMapping("/{venueId}")
    public VenueResponse deleteVenue(@PathVariable String venueId) {
        long id;
        try {
            id = Long.parseLong(venueId);
        } catch (NumberFormatException exception) {
            throw new edu.mec.studentportal.api.ApiException(
                HttpStatus.BAD_REQUEST,
                edu.mec.studentportal.api.ApiErrorCodes.INVALID_REQUEST,
                "venue id must be a number."
            );
        }
        return venueService.deleteVenue(id);
    }
}
