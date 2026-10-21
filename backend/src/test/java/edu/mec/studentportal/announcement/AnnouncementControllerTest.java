package edu.mec.studentportal.announcement;

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

/** HTTP contract for announcement endpoints. */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class AnnouncementControllerTest extends DatabaseIntegrationTestSupport {
    @Test
    void publishReturnsCreatedAnnouncement() throws Exception {
        long organizer = createOrganizer();
        createStudent();

        mockMvc.perform(post("/api/announcements")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, organizer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hello\",\"content\":\"World\",\"audienceRole\":null}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.title").value("Hello"))
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.audienceRole").doesNotExist());
    }

    @Test
    void publishRejectsInvalidAudience() throws Exception {
        long admin = createAdmin();

        mockMvc.perform(post("/api/announcements")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hello\",\"content\":\"World\",\"audienceRole\":\"PRINCIPAL\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void studentCannotPublish() throws Exception {
        long student = createStudent();

        mockMvc.perform(post("/api/announcements")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hello\",\"content\":\"World\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void archiveReturnsArchivedStatus() throws Exception {
        long organizer = createOrganizer();
        long announcementId = createAnnouncementViaHttp(organizer);

        mockMvc.perform(post("/api/announcements/{id}/archive", announcementId)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, organizer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ARCHIVED"))
            .andExpect(jsonPath("$.archivedAt").exists());
    }

    @Test
    void studentListsMatchingPublishedAnnouncements() throws Exception {
        long organizer = createOrganizer();
        long student = createStudent();
        createAnnouncementViaHttp(organizer, null);
        createAnnouncementViaHttp(organizer, "STUDENT");
        createAnnouncementViaHttp(organizer, "FACULTY");

        mockMvc.perform(get("/api/announcements")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    private long createAnnouncementViaHttp(long organizer) throws Exception {
        return createAnnouncementViaHttp(organizer, null);
    }

    private long createAnnouncementViaHttp(long organizer, String audienceRole) throws Exception {
        String body = audienceRole == null
            ? "{\"title\":\"T\",\"content\":\"C\",\"audienceRole\":null}"
            : "{\"title\":\"T\",\"content\":\"C\",\"audienceRole\":\"" + audienceRole + "\"}";
        mockMvc.perform(post("/api/announcements")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, organizer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM announcements", Long.class);
    }
}
