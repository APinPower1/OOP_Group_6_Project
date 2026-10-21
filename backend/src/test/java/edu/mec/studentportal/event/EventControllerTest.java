package edu.mec.studentportal.event;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** HTTP contract for the event endpoints: status codes, error codes and JSON shape. */
@EnabledIf(
    value = "edu.mec.studentportal.event.EventTestDatabase#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class EventControllerTest extends EventIntegrationTestSupport {
    private static String body(String venue, String start, String end, int capacity) {
        return """
            {
              "title": "Open Source Day",
              "description": "Talks and workshops",
              "venue": "%s",
              "startTime": "%s",
              "endTime": "%s",
              "capacity": %d
            }
            """.formatted(venue, start, end, capacity);
    }

    private static String farFutureStart() {
        return LocalDateTime.now(EventService.CAMPUS_ZONE).plusDays(14)
            .withHour(10).withMinute(0).withSecond(0).withNano(0).toString();
    }

    private static String farFutureEnd() {
        return LocalDateTime.now(EventService.CAMPUS_ZONE).withHour(10).plusDays(14)
            .plusHours(6).withMinute(0).withSecond(0).withNano(0).toString();
    }

    @Test
    void createWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Auditorium A", farFutureStart(), farFutureEnd(), 100)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void organizerCreateReturns201WithContractShape() throws Exception {
        long organizer = createOrganizer();

        mockMvc.perform(post("/api/events")
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Auditorium A", farFutureStart(), farFutureEnd(), 120)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.title").value("Open Source Day"))
            .andExpect(jsonPath("$.description").value("Talks and workshops"))
            .andExpect(jsonPath("$.venue").value("Auditorium A"))
            .andExpect(jsonPath("$.startTime").exists())
            .andExpect(jsonPath("$.endTime").exists())
            .andExpect(jsonPath("$.capacity").value(120))
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.createdBy").value(organizer))
            .andExpect(jsonPath("$.reviewedBy").doesNotExist())
            .andExpect(jsonPath("$.registeredCount").value(0))
            .andExpect(jsonPath("$.availableSlots").value(120));
    }

    @Test
    void createWithInvalidBodyReturns400() throws Exception {
        long organizer = createOrganizer();

        mockMvc.perform(post("/api/events")
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Auditorium A", farFutureEnd(), farFutureStart(), 100)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/events")
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void studentCreateReturns403() throws Exception {
        long student = createStudent();

        mockMvc.perform(post("/api/events")
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Auditorium A", farFutureStart(), farFutureEnd(), 100)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void venueConflictReturns409WithStableCode() throws Exception {
        long organizer = createOrganizer();
        LocalDateTime start = now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);
        insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium A", start, start.plusHours(4), 50);

        mockMvc.perform(post("/api/events")
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Auditorium A", start.plusHours(1).toString(), start.plusHours(5).toString(), 30)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VENUE_CONFLICT"))
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void ownerUpdateResubmitsAsPending() throws Exception {
        long organizer = createOrganizer();
        long eventId = insertEventRow(organizer, "REJECTED", "Auditorium A", now().plusDays(7), 50);

        mockMvc.perform(put("/api/events/{eventId}", eventId)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Seminar Hall B", farFutureStart(), farFutureEnd(), 80)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(eventId))
            .andExpect(jsonPath("$.venue").value("Seminar Hall B"))
            .andExpect(jsonPath("$.capacity").value(80))
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        assertEventStatus(eventId, "PENDING_APPROVAL");
    }

    @Test
    void updateByNonOwnerReturns403() throws Exception {
        long owner = createOrganizer();
        long other = createOrganizer();
        long eventId = insertEventRow(owner, "PENDING_APPROVAL", now().plusDays(7), 50);

        mockMvc.perform(put("/api/events/{eventId}", eventId)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Seminar Hall B", farFutureStart(), farFutureEnd(), 80)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void facultyApproveReturns200StudentApproveReturns403() throws Exception {
        long organizer = createOrganizer();
        long faculty = createFaculty();
        long student = createStudent();
        long eventId = insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium A", now().plusDays(7), 50);

        mockMvc.perform(post("/api/events/{eventId}/approve", eventId)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/events/{eventId}/approve", eventId)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, faculty))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.reviewedBy").value(faculty));

        assertEventStatus(eventId, "APPROVED");
    }

    @Test
    void rejectWithoutReasonReturns400WithReasonReturns200() throws Exception {
        long organizer = createOrganizer();
        long faculty = createFaculty();
        long missing = insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium A", now().plusDays(7), 50);
        long valid = insertEventRow(organizer, "PENDING_APPROVAL", "Auditorium B", now().plusDays(9), 50);

        mockMvc.perform(post("/api/events/{eventId}/reject", missing)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, faculty)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/events/{eventId}/reject", valid)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, faculty)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Venue unavailable\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.reviewReason").value("Venue unavailable"));
    }

    @Test
    void cancelCancelsEventAndRegistrations() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        long eventId = insertEventRow(organizer, "APPROVED", "Auditorium A", now().plusDays(7), 50);
        insertRegistrationRow(student, eventId, "CONFIRMED");

        mockMvc.perform(post("/api/events/{eventId}/cancel", eventId)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.registeredCount").value(0));

        assertEventStatus(eventId, "CANCELLED");
        org.assertj.core.api.Assertions.assertThat(confirmedCount(eventId)).isZero();
        org.assertj.core.api.Assertions.assertThat(eventCancelledNotificationCount(eventId)).isEqualTo(1);
    }

    @Test
    void listReturnsRoleScopedEvents() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        long pending = insertEventRow(organizer, "PENDING_APPROVAL", now().plusDays(7), 50);
        long approved = insertEventRow(organizer, "APPROVED", now().plusDays(8), 50);

        mockMvc.perform(get("/api/events")
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(approved));

        mockMvc.perform(get("/api/events")
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/events")
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer)
                .param("status", "PENDING_APPROVAL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(pending));
    }

    @Test
    void getHiddenEventReturns404ForStudent() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        long pending = insertEventRow(organizer, "PENDING_APPROVAL", now().plusDays(7), 50);

        mockMvc.perform(get("/api/events/{eventId}", pending)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

        mockMvc.perform(get("/api/events/{eventId}", pending)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pending));
    }

    @Test
    void unknownEventReturns404() throws Exception {
        long organizer = createOrganizer();

        mockMvc.perform(get("/api/events/{eventId}", 999_999_999L)
                .header(EventTestSecurityConfig.TEST_AUTH_HEADER, organizer))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }
}
