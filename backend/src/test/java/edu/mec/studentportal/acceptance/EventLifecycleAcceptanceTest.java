package edu.mec.studentportal.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance scenario 2 (MEMBER-09): proposal lifecycle through the real API — creation is
 * always PENDING_APPROVAL, students only learn about approved events, only Faculty/Admin
 * moderate, rejection requires a reason, and venue/time overlap is refused both at create
 * and at the approval-time re-check.
 */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class EventLifecycleAcceptanceTest extends AcceptanceTestSupport {

    private long organizerId;
    private long otherOrganizerId;
    private long facultyId;
    private long studentId;

    private void seedUsers() {
        organizerId = createUser("ORGANIZER");
        otherOrganizerId = createUser("ORGANIZER");
        facultyId = createUser("FACULTY");
        studentId = createUser("STUDENT");
    }

    @Test
    void proposalBecomesVisibleToStudentsOnlyAfterFacultyApproval() throws Exception {
        seedUsers();
        LocalDateTime start = now().plusDays(2);
        JsonNode event = createEventViaApi(
            bearerFor(organizerId), "Lifecycle Talk", "Auditorium L1",
            start, start.plusHours(2), 40);
        long eventId = event.path("id").asLong();

        assertEquals("PENDING_APPROVAL", event.path("status").asText());
        assertEquals(organizerId, event.path("createdBy").asLong());
        assertTrue(!event.hasNonNull("reviewedBy") || event.get("reviewedBy").isNull(),
            "client cannot fake reviewer metadata");

        Set<Long> studentFeed = eventIds(getJson(bearerFor(studentId), "/api/events"));
        assertFalse(studentFeed.contains(eventId),
            "pending proposal must be invisible to students");

        Set<Long> facultyPending = eventIds(getJson(
            bearerFor(facultyId), "/api/events?status=PENDING_APPROVAL"));
        assertTrue(facultyPending.contains(eventId),
            "faculty must see the pending proposal in the review queue");

        mockMvc.perform(post("/api/events/" + eventId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(facultyId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        studentFeed = eventIds(getJson(bearerFor(studentId), "/api/events"));
        assertTrue(studentFeed.contains(eventId),
            "students must see the event after approval");
    }

    @Test
    void nonFacultyCannotModerateAndNonOwnerCannotEdit() throws Exception {
        seedUsers();
        LocalDateTime start = now().plusDays(3);
        JsonNode event = createEventViaApi(
            bearerFor(organizerId), "Protected Proposal", "Auditorium L2",
            start, start.plusHours(1), 20);
        long eventId = event.path("id").asLong();

        mockMvc.perform(post("/api/events/" + eventId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/events/" + eventId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(otherOrganizerId)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/events/" + eventId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(otherOrganizerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"not mine to reject"}"""))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/events/" + eventId)
                .header(HttpHeaders.AUTHORIZATION, bearerFor(otherOrganizerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Hijacked","description":"x","venue":"Auditorium L2",
                     "startTime":"2027-04-01T10:00:00","endTime":"2027-04-01T12:00:00",
                     "capacity":20}"""))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectRequiresReasonAndStoresItForTheOrganizer() throws Exception {
        seedUsers();
        LocalDateTime start = now().plusDays(4);
        JsonNode event = createEventViaApi(
            bearerFor(organizerId), "Rejectable Proposal", "Auditorium L3",
            start, start.plusHours(2), 30);
        long eventId = event.path("id").asLong();

        mockMvc.perform(post("/api/events/" + eventId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(facultyId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":" "}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/events/" + eventId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(facultyId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"Venue unavailable that day."}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.reviewReason").value("Venue unavailable that day."));

        JsonNode organizerView = getJson(bearerFor(organizerId), "/api/events/" + eventId);
        assertEquals("REJECTED", organizerView.path("status").asText());
        assertEquals("Venue unavailable that day.",
            organizerView.path("reviewReason").asText());
    }

    @Test
    void overlappingProposalIsRefusedAtCreation() throws Exception {
        seedUsers();
        LocalDateTime start = now().plusDays(5).withHour(10).withMinute(0);
        createEventViaApi(bearerFor(organizerId), "First Booking", "Shared Hall A",
            start, start.plusHours(2), 25);

        mockMvc.perform(post("/api/events")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(otherOrganizerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Clashing Booking","description":"x","venue":"shared hall a",
                     "startTime":"%s","endTime":"%s","capacity":25}"""
                    .formatted(
                        start.plusHours(1).format(java.time.format.DateTimeFormatter
                            .ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                        start.plusHours(3).format(java.time.format.DateTimeFormatter
                            .ofPattern("yyyy-MM-dd'T'HH:mm:ss")))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VENUE_CONFLICT"));
    }

    @Test
    void overlapIsRecheckedWhenFacultyApproves() throws Exception {
        seedUsers();
        LocalDateTime firstStart = now().plusDays(6).withHour(10).withMinute(0);
        LocalDateTime secondStart = now().plusDays(6).withHour(15).withMinute(0);

        JsonNode first = createEventViaApi(bearerFor(organizerId), "Morning Slot",
            "Shared Hall B", firstStart, firstStart.plusHours(2), 25);
        JsonNode second = createEventViaApi(bearerFor(otherOrganizerId), "Evening Slot",
            "Shared Hall B", secondStart, secondStart.plusHours(2), 25);
        long firstId = first.path("id").asLong();
        long secondId = second.path("id").asLong();

        mockMvc.perform(post("/api/events/" + firstId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(facultyId)))
            .andExpect(status().isOk());

        jdbcTemplate.update(
            "UPDATE events SET start_time = ?, end_time = ? WHERE id = ?",
            java.sql.Timestamp.valueOf(firstStart),
            java.sql.Timestamp.valueOf(firstStart.plusHours(2)),
            secondId
        );

        mockMvc.perform(post("/api/events/" + secondId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(facultyId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VENUE_CONFLICT"));
    }

    private Set<Long> eventIds(JsonNode feed) {
        Set<Long> ids = new HashSet<>();
        if (feed.isArray()) {
            feed.forEach(row -> ids.add(row.path("id").asLong()));
        }
        return ids;
    }
}
