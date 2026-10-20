package edu.mec.studentportal.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.mec.studentportal.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

/**
 * Database-free proof that a racing writer hitting the (student_id, event_id) unique
 * constraint surfaces as the stable 409 DUPLICATE_REGISTRATION API error instead of a 500,
 * even when the race slips past the event-row lock.
 */
class RegistrationDuplicateRaceMappingTest {
    private static final long EVENT_ID = 12L;
    private static final long STUDENT_ID = 7L;
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-10-18T14:00:00Z"),
        ZoneId.of("Asia/Kolkata")
    );

    private RegistrationDAO dao;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        dao = mock(RegistrationDAO.class);
        service = new RegistrationService(dao, FIXED_CLOCK);
    }

    @Test
    void mapsUniqueConstraintViolationToDuplicateRegistrationConflict() {
        when(dao.lockEvent(EVENT_ID)).thenReturn(Optional.of(
            new RegistrationDAO.EventLockRow(
                EVENT_ID,
                RegistrationDAO.EVENT_STATUS_APPROVED,
                LocalDateTime.now(FIXED_CLOCK).plusHours(2),
                30,
                1L
            )
        ));
        when(dao.findUser(STUDENT_ID)).thenReturn(Optional.of(
            new RegistrationDAO.UserRoleRow(STUDENT_ID, RegistrationDAO.ROLE_STUDENT, true)
        ));
        when(dao.findByStudentAndEvent(STUDENT_ID, EVENT_ID)).thenReturn(Optional.empty());
        when(dao.countConfirmed(EVENT_ID)).thenReturn(0);
        when(dao.insert(anyLong(), anyLong(), anyString(), any())).thenThrow(
            new DuplicateKeyException("Duplicate entry for key 'uq_registration_student_event'")
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.register(EVENT_ID, STUDENT_ID));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(RegistrationErrorCodes.DUPLICATE_REGISTRATION);
        assertThat(exception.getMessage()).isNotBlank();
    }
}
