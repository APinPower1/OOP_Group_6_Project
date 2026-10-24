package edu.mec.studentportal.venue;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {
    public static final int MAX_NAME_LENGTH = 120;

    private final VenueDao venueDao;

    public VenueService(VenueDao venueDao) {
        this.venueDao = venueDao;
    }

    public List<VenueResponse> listVenues() {
        return venueDao.list().stream()
            .map(venue -> new VenueResponse(venue.id(), venue.name()))
            .toList();
    }

    @Transactional
    public VenueResponse createVenue(VenueCreateRequest request) {
        String name = requireName(request == null ? null : request.name());
        try {
            Venue venue = venueDao.insert(name);
            return new VenueResponse(venue.id(), venue.name());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCodes.CONFLICT,
                "Venue \"" + name + "\" already exists."
            );
        }
    }

    @Transactional
    public VenueResponse deleteVenue(long venueId) {
        if (venueId <= 0) {
            throw invalid("venue id must be positive.");
        }
        int deleted = venueDao.delete(venueId);
        if (deleted != 1) {
            throw new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCodes.NOT_FOUND,
                "Venue not found."
            );
        }
        return new VenueResponse(venueId, null);
    }

    private static String requireName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw invalid("name is required.");
        }
        String name = rawName.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            throw invalid("name must be at most " + MAX_NAME_LENGTH
                + " characters.");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw invalid("name must not contain control characters.");
        }
        return name;
    }

    private static ApiException invalid(String message) {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.INVALID_REQUEST,
            message
        );
    }
}
