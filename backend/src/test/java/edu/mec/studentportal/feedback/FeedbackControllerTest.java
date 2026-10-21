package edu.mec.studentportal.feedback;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.mec.studentportal.testsupport.DatabaseIntegrationTestSupport;
import edu.mec.studentportal.testsupport.TestAuthSecurityConfig;
import edu.mec.studentportal.testsupport.TestDatabaseAvailability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;

/** HTTP contract for feedback endpoints. */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class FeedbackControllerTest extends DatabaseIntegrationTestSupport {
    @Test
    void submitReturnsCreatedFeedback() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(student, event, "CONFIRMED");

        mockMvc.perform(post("/api/events/{eventId}/feedback", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"comments\":\"Well organized\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventId").value(event))
            .andExpect(jsonPath("$.rating").value(5))
            .andExpect(jsonPath("$.studentId").value(student))
            .andExpect(jsonPath("$.submittedAt").exists());
    }

    @Test
    void ratingOutOfBoundsIsRejected() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(student, event, "CONFIRMED");

        mockMvc.perform(post("/api/events/{eventId}/feedback", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":6}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void ownerSeesFullRecordsStudentIsDenied() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(student, event, "CONFIRMED");
        insertFeedback(student, event, 4);

        mockMvc.perform(get("/api/events/{eventId}/feedback", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, organizer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].rating").value(4));

        mockMvc.perform(get("/api/events/{eventId}/feedback", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isForbidden());
    }

    @Test
    void summaryIsVisibleToAnySignedInViewer() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        long viewer = createStudent();
        long event = createEndedEvent(organizer, 5);
        insertRegistration(student, event, "CONFIRMED");
        insertFeedback(student, event, 5);

        mockMvc.perform(get("/api/events/{eventId}/feedback/summary", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, viewer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value(event))
            .andExpect(jsonPath("$.ratingCount").value(1))
            .andExpect(jsonPath("$.averageRating").value(5.0));
    }

    private void insertFeedback(long studentId, long eventId, int rating) {
        jdbcTemplate.update(
            "INSERT INTO feedback (student_id, event_id, rating, comments, submitted_at) VALUES (?, ?, ?, NULL, ?)",
            studentId,
            eventId,
            rating,
            java.sql.Timestamp.valueOf(now())
        );
    }
}
