package edu.mec.studentportal.client.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Client DTO JSON mapping must match docs/API_CONTRACT.md field names: ISO date-times,
 * unknown fields tolerated (backend may add fields without breaking old clients).
 */
class DtoMappingTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void eventDtoDeserializesContractJson() throws Exception {
        String json = """
            {"id":42,"title":"Campus Fest","description":"desc","venue":"Main Hall",
             "startTime":"2026-03-10T10:00:00","endTime":"2026-03-10T16:00:00",
             "capacity":120,"status":"APPROVED","createdBy":5,"reviewedBy":2,
             "reviewReason":null,"createdAt":"2026-03-01T08:00:00",
             "updatedAt":"2026-03-02T09:30:00","registeredCount":17,"availableSlots":103,
             "futureServerField":"ignored"}""";

        EventDto event = mapper.readValue(json, EventDto.class);

        assertEquals(42, event.id());
        assertEquals("Campus Fest", event.title());
        assertEquals(LocalDateTime.of(2026, 3, 10, 10, 0), event.startTime());
        assertEquals(LocalDateTime.of(2026, 3, 10, 16, 0), event.endTime());
        assertEquals(EventStatus.APPROVED, event.status());
        assertEquals(120, event.capacity());
        assertEquals(17, event.registeredCount());
        assertEquals(103, event.availableSlots());
        assertEquals(5L, event.createdBy());
        assertEquals(2L, event.reviewedBy());
    }

    @Test
    void loginResponseMapsToSessionUser() throws Exception {
        String json = """
            {"accessToken":"jwt-here","tokenType":"Bearer","expiresInSeconds":3600,
             "user":{"id":7,"fullName":"Std One","email":"s@mec.edu","role":"STUDENT"}}""";

        LoginResponse response = mapper.readValue(json, LoginResponse.class);
        UserSummary user = response.user().toUserSummary();

        assertEquals("jwt-here", response.accessToken());
        assertEquals(3600, response.expiresInSeconds());
        assertEquals(7, user.id());
        assertEquals(Role.STUDENT, user.role());
        assertEquals("Std One", user.fullName());
    }

    @Test
    void participationReportDeserializesNestedShape() throws Exception {
        String json = """
            {"events":{"total":4,"pendingApproval":1,"approved":2,"rejected":1,"cancelled":0},
             "registrations":{"total":9,"confirmed":7,"cancelled":2},
             "feedback":{"count":5,"averageRating":4.2},
             "participation":[
               {"eventId":1,"title":"Fest","status":"APPROVED","capacity":120,
                "confirmedCount":7,"cancelledCount":2,"feedbackCount":5,"averageRating":4.2},
               {"eventId":2,"title":"Talk","status":"PENDING_APPROVAL","capacity":50,
                "confirmedCount":0,"cancelledCount":0,"feedbackCount":0,"averageRating":null}
             ]}""";

        ParticipationReportDto report = mapper.readValue(json, ParticipationReportDto.class);

        assertEquals(4, report.events().total());
        assertEquals(1, report.events().pendingApproval());
        assertEquals(9, report.registrations().total());
        assertEquals(7, report.registrations().confirmed());
        assertEquals(5, report.feedback().count());
        assertEquals(4.2, report.feedback().averageRating());
        assertEquals(2, report.participation().size());
        assertNull(report.participation().get(1).averageRating(),
            "rating must stay null when no feedback exists");
        assertEquals("Fest", report.participation().get(0).title());
    }

    @Test
    void nullParticipationBecomesEmptyList() {
        ParticipationReportDto report = new ParticipationReportDto(
            null, null, null, null);
        assertTrue(report.participation().isEmpty());
    }

    @Test
    void apiErrorBodyParsesStandardErrorShape() throws Exception {
        String json = """
            {"timestamp":"2026-01-15T12:00:00Z","status":409,"code":"VENUE_CONFLICT",
             "message":"Venue overlaps an approved event.","path":"/api/events"}""";

        ApiErrorBody body = mapper.readValue(json, ApiErrorBody.class);

        assertEquals(409, body.status());
        assertEquals("VENUE_CONFLICT", body.code());
        assertEquals("Venue overlaps an approved event.", body.message());
        assertEquals("/api/events", body.path());
    }

    @Test
    void roleNamesMatchApiContract() {
        assertEquals(List.of("STUDENT", "ORGANIZER", "FACULTY", "ADMIN"),
            List.of(Role.values()).stream().map(Role::name).toList());
    }
}
