package edu.mec.studentportal.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance scenario 3 (MEMBER-09): registration lifecycle (seat consumption, duplicate
 * refusal, cancellation releasing capacity, EVENT_FULL) and feedback eligibility rules
 * (ended event only, confirmed registration only, one per student, ratings 1-5) — all through
 * the real JWT chain.
 */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class RegistrationAndFeedbackAcceptanceTest extends AcceptanceTestSupport {

    private long organizerId;
    private long facultyId;
    private long studentId;

    private void seedUsers() {
        organizerId = createUser("ORGANIZER");
        facultyId = createUser("FACULTY");
        studentId = createUser("STUDENT");
    }

    @Test
    void registerDuplicateCancelAndReactivateConsumesAndReleasesSeats() throws Exception {
        seedUsers();
        long eventId = createApprovedEvent(
            organizerId, facultyId, "Seat Hall 1", now().plusDays(2), 3);

        assertEquals(3, availableSlots(eventId), "approved event starts with all seats free");

        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.ticketNo").isNotEmpty());

        assertEquals(2, availableSlots(eventId), "confirming a seat must reduce available slots");

        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_REGISTRATION"));

        mockMvc.perform(delete("/api/events/" + eventId + "/registrations/me")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(3, availableSlots(eventId), "cancellation must release the seat");

        JsonNode history = getJson(bearerFor(studentId), "/api/registrations/me");
        assertTrue(history.isArray() && history.size() >= 1,
            "registration history must retain the cancelled ticket");

        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertEquals(2, availableSlots(eventId),
            "reactivated registration must consume exactly one seat again");
    }

    @Test
    void fullEventRefusesFurtherRegistrations() throws Exception {
        seedUsers();
        long eventId = createApprovedEvent(
            organizerId, facultyId, "Seat Hall 2", now().plusDays(3), 1);
        long otherStudentId = createUser("STUDENT");

        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isCreated());

        assertEquals(0, availableSlots(eventId));

        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(otherStudentId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_FULL"));
    }

    @Test
    void pendingEventRefusesRegistration() throws Exception {
        seedUsers();
        LocalDateTime start = now().plusDays(4);
        JsonNode pending = createEventViaApi(
            bearerFor(organizerId), "Still Pending", "Seat Hall 3",
            start, start.plusHours(2), 10);
        long eventId = pending.path("id").asLong();

        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_APPROVED"));
    }

    @Test
    void feedbackRequiresEndedEventConfirmedSeatAndSingleSubmission() throws Exception {
        seedUsers();
        long eventId = createApprovedEvent(
            organizerId, facultyId, "Feedback Hall", now().plusDays(2), 10);
        long otherStudentId = createUser("STUDENT");

        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isCreated());

        String earlyBody = """
            {"rating":5,"comments":"Great."}""";
        mockMvc.perform(post("/api/events/" + eventId + "/feedback")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(earlyBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_ALLOWED"));

        mockMvc.perform(post("/api/events/" + eventId + "/feedback")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(otherStudentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(earlyBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_ALLOWED"));

        jdbcTemplate.update(
            "UPDATE events SET start_time = ?, end_time = ? WHERE id = ?",
            java.sql.Timestamp.valueOf(now().minusHours(3)),
            java.sql.Timestamp.valueOf(now().minusHours(1)),
            eventId
        );

        mockMvc.perform(post("/api/events/" + eventId + "/feedback")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(otherStudentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(earlyBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_ALLOWED"));

        mockMvc.perform(post("/api/events/" + eventId + "/feedback")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(earlyBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.rating").value(5));

        mockMvc.perform(post("/api/events/" + eventId + "/feedback")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(earlyBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_FEEDBACK"));

        mockMvc.perform(post("/api/events/" + eventId + "/feedback")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rating":6,"comments":"Out of range."}"""))
            .andExpect(status().isBadRequest());

        JsonNode summary = getJson(bearerFor(studentId),
            "/api/events/" + eventId + "/feedback/summary");
        assertEquals(1, summary.path("ratingCount").asInt());
        assertEquals(5.0, summary.path("averageRating").asDouble(), 0.0001);
        assertFalse(summary.toString().contains(emailFor(studentId)),
            "summary must not expose student identity");
    }
}
