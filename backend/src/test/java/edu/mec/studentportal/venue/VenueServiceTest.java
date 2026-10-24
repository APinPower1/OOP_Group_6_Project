package edu.mec.studentportal.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {
    @Mock
    private VenueDao venueDao;

    @InjectMocks
    private VenueService venueService;

    @Test
    void listsVenuesOrderedByDao() {
        when(venueDao.list()).thenReturn(List.of(
            new Venue(1L, "Main Auditorium"),
            new Venue(2L, "Seminar Hall A")
        ));

        List<VenueResponse> venues = venueService.listVenues();

        assertThat(venues).containsExactly(
            new VenueResponse(1L, "Main Auditorium"),
            new VenueResponse(2L, "Seminar Hall A")
        );
    }

    @Test
    void createsVenueWithTrimmedName() {
        when(venueDao.insert("Seminar Hall B"))
            .thenReturn(new Venue(3L, "Seminar Hall B"));

        VenueResponse response =
            venueService.createVenue(new VenueCreateRequest("  Seminar Hall B  "));

        assertThat(response).isEqualTo(new VenueResponse(3L, "Seminar Hall B"));
        verify(venueDao).insert("Seminar Hall B");
    }

    @Test
    void rejectsBlankVenueName() {
        Throwable thrown = catchThrowable(
            () -> venueService.createVenue(new VenueCreateRequest("   "))
        );

        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException apiException = (ApiException) thrown;
        assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(apiException.getCode()).isEqualTo(ApiErrorCodes.INVALID_REQUEST);
        assertThat(apiException.getMessage()).isEqualTo("name is required.");
    }

    @Test
    void rejectsDuplicateVenueName() {
        when(venueDao.insert("Main Auditorium"))
            .thenThrow(new DuplicateKeyException("duplicate"));

        Throwable thrown = catchThrowable(
            () -> venueService.createVenue(new VenueCreateRequest("Main Auditorium"))
        );

        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException apiException = (ApiException) thrown;
        assertThat(apiException.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(apiException.getMessage())
            .isEqualTo("Venue \"Main Auditorium\" already exists.");
    }

    @Test
    void deletesExistingVenue() {
        when(venueDao.delete(5L)).thenReturn(1);

        VenueResponse response = venueService.deleteVenue(5L);

        assertThat(response.id()).isEqualTo(5L);
        verify(venueDao).delete(5L);
    }

    @Test
    void rejectsDeletingUnknownVenue() {
        when(venueDao.delete(77L)).thenReturn(0);

        Throwable thrown = catchThrowable(() -> venueService.deleteVenue(77L));

        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException apiException = (ApiException) thrown;
        assertThat(apiException.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(apiException.getCode()).isEqualTo(ApiErrorCodes.NOT_FOUND);
    }

    @Test
    void rejectsNonPositiveVenueId() {
        Throwable thrown = catchThrowable(() -> venueService.deleteVenue(0L));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getStatus())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
