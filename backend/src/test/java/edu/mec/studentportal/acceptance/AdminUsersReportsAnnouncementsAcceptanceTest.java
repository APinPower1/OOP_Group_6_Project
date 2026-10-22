package edu.mec.studentportal.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance scenarios 4-6 (MEMBER-09): admin user lifecycle (create with role profile,
 * disable revoking future requests), Admin-only participation reporting against data seeded
 * through the real API, announcement audience/privacy of notifications, and club management.
 */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class AdminUsersReportsAnnouncementsAcceptanceTest extends AcceptanceTestSupport {

    @Test
    void adminCreatesFiltersAndDisablesUsersThroughTheRealApi() throws Exception {
        long adminId = createUser("ADMIN");
        String admin = bearerFor(adminId);

        mockMvc.perform(post("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"New Student","email":"new.student@test.example.edu",
                     "department":"CSE","role":"STUDENT","password":"Initial-Pass-123",
                     "profile":{"rollNumber":"R-ACC-1","semester":4,"batch":"2026"}}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.profile.rollNumber").value("R-ACC-1"));

        JsonNode page = getJson(admin, "/api/admin/users?role=STUDENT&q=new.student");
        assertEquals(1, page.path("totalItems").asInt());
        long createdId = page.path("items").get(0).path("id").asLong();
        assertTrue(page.path("items").get(0).path("email").asText()
            .contains("new.student"));

        JsonNode facultyOnly = getJson(admin, "/api/admin/users?role=FACULTY");
        for (JsonNode row : facultyOnly.path("items")) {
            assertFalse(row.path("email").asText().contains("new.student"),
                "role filter must exclude other roles");
        }

        String newBearer = "Bearer " + bearerToken("new.student@test.example.edu",
            "Initial-Pass-123");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, newBearer))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/" + createdId + "/disable")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, newBearer))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"new.student@test.example.edu","password":"Initial-Pass-123"}"""))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/users/" + createdId + "/enable")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"new.student@test.example.edu","password":"Initial-Pass-123"}"""))
            .andExpect(status().isOk());
    }

    @Test
    void participationReportIsAdminOnlyAndReflectsRealApiData() throws Exception {
        long adminId = createUser("ADMIN");
        long organizerId = createUser("ORGANIZER");
        long facultyId = createUser("FACULTY");
        long studentId = createUser("STUDENT");
        long eventId = createApprovedEvent(
            organizerId, facultyId, "Report Hall", now().plusDays(2), 20);

        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/reports/participation")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(organizerId)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports/participation")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isForbidden());

        JsonNode report = getJson(bearerFor(adminId), "/api/admin/reports/participation");
        assertTrue(report.path("events").path("approved").asLong() >= 1);
        assertEquals(1, report.path("registrations").path("confirmed").asLong());

        JsonNode rows = report.path("participation");
        assertTrue(rows.isArray() && !rows.isEmpty(), "participation rows expected");
        boolean found = false;
        for (JsonNode row : rows) {
            if (row.path("eventId").asLong() == eventId) {
                found = true;
                assertEquals(1, row.path("confirmedCount").asLong());
                assertEquals(20, row.path("capacity").asInt());
                assertEquals("APPROVED", row.path("status").asText());
            }
        }
        assertTrue(found, "seeded event must appear in the participation table");
        String raw = report.toString();
        assertFalse(raw.contains(emailFor(studentId)),
            "report must aggregate only, never student identities");
    }

    @Test
    void announcementAudienceDrivesNotificationsAndPrivacyIsEnforced() throws Exception {
        long organizerId = createUser("ORGANIZER");
        long studentId = createUser("STUDENT");
        long otherStudentId = createUser("STUDENT");

        mockMvc.perform(post("/api/announcements")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(organizerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Registration Opens","content":"Opens Monday.",
                     "audienceRole":"STUDENT"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Registration Opens"));

        JsonNode studentFeed = getJson(bearerFor(studentId), "/api/announcements");
        assertTrue(studentFeed.isArray() && studentFeed.size() >= 1,
            "student must see the student-audience announcement");

        JsonNode studentNotifications = getJson(bearerFor(studentId), "/api/notifications/me");
        assertTrue(studentNotifications.isArray() && studentNotifications.size() >= 1);
        long notificationId = studentNotifications.get(0).path("id").asLong();
        assertTrue(studentNotifications.get(0).toString().contains("Registration Opens"));

        JsonNode organizerNotifications = getJson(bearerFor(organizerId),
            "/api/notifications/me");
        assertEquals(0, organizerNotifications.size(),
            "non-audience staff must not receive the notification");

        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(otherStudentId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isOk());

        JsonNode afterRead = getJson(bearerFor(studentId), "/api/notifications/me");
        Set<Long> readIds = new HashSet<>();
        afterRead.forEach(row -> {
            if (row.hasNonNull("readAt") && row.get("readAt").isTextual()) {
                readIds.add(row.path("id").asLong());
            }
        });
        assertTrue(readIds.contains(notificationId), "own notification must be marked read");
    }

    @Test
    void adminManagesClubsAndStudentsControlOnlyTheirMembership() throws Exception {
        long adminId = createUser("ADMIN");
        long studentId = createUser("STUDENT");
        String admin = bearerFor(adminId);

        mockMvc.perform(post("/api/clubs")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Robotics Club","description":"Build and compete.",
                     "category":"Technical"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Robotics Club"));

        mockMvc.perform(post("/api/clubs")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Rogue Club","description":"x","category":"Other"}"""))
            .andExpect(status().isForbidden());

        JsonNode clubs = getJson(bearerFor(studentId), "/api/clubs");
        long clubId = -1;
        for (JsonNode club : clubs) {
            if ("Robotics Club".equals(club.path("name").asText())) {
                clubId = club.path("id").asLong();
            }
        }
        assertTrue(clubId > 0, "active club must be listed for students");

        mockMvc.perform(post("/api/clubs/" + clubId + "/membership")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clubs/" + clubId + "/membership")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isCreated());

        JsonNode members = getJson(admin, "/api/clubs/" + clubId + "/members");
        assertTrue(members.isArray() && members.size() == 1,
            "duplicate join must not create a second membership row");
        assertEquals("ACC-R-" + studentId, members.get(0).path("rollNumber").asText());

        mockMvc.perform(delete("/api/clubs/" + clubId + "/membership")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/clubs/" + clubId + "/deactivate")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/clubs/" + clubId + "/membership")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(studentId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLUB_INACTIVE"));
    }
}
