package edu.mec.studentportal.club;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mec.studentportal.testsupport.TestAuthSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** HTTP contract for the club endpoints: status codes, error codes and JSON shape. */
@EnabledIf(
    value = "edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class ClubControllerTest extends ClubIntegrationTestSupport {
    @Autowired
    ObjectMapper objectMapper;

    private String json(ClubRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void listWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/clubs"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void adminCreatesClubReturns201() throws Exception {
        long admin = createAdmin();
        String name = "Robotics " + System.nanoTime();

        mockMvc.perform(post("/api/clubs")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(name))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.activeMemberCount").value(0));
    }

    @Test
    void studentCreateIsForbidden() throws Exception {
        long student = createStudent();

        mockMvc.perform(post("/api/clubs")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request("Nope " + System.nanoTime()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        long admin = createAdmin();

        mockMvc.perform(post("/api/clubs")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"description\":\"d\",\"category\":\"c\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void duplicateNameReturns409() throws Exception {
        long admin = createAdmin();
        String name = "Dup " + System.nanoTime();
        clubService.create(request(name), admin);

        mockMvc.perform(post("/api/clubs")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(name))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLUB_NAME_TAKEN"));
    }

    @Test
    void listAndGetReturnActiveClubs() throws Exception {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Listing " + System.nanoTime()), admin);
        clubService.join(club.id(), student);

        mockMvc.perform(get("/api/clubs")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == %d)]".formatted(club.id())).exists());

        mockMvc.perform(get("/api/clubs/{id}", club.id())
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(club.id()))
            .andExpect(jsonPath("$.activeMemberCount").value(1));
    }

    @Test
    void getUnknownClubReturns404() throws Exception {
        long student = createStudent();

        mockMvc.perform(get("/api/clubs/{id}", 999_999_999L)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CLUB_NOT_FOUND"));
    }

    @Test
    void updateByAdminReturns200() throws Exception {
        long admin = createAdmin();
        ClubResponse club = clubService.create(request("Update " + System.nanoTime()), admin);

        mockMvc.perform(put("/api/clubs/{id}", club.id())
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ClubRequest("Renamed Society", "Updated.", "CULTURAL"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(club.id()))
            .andExpect(jsonPath("$.name").value("Renamed Society"))
            .andExpect(jsonPath("$.category").value("CULTURAL"));
    }

    @Test
    void deactivateByAdminReturns200AndHidesFromListing() throws Exception {
        long admin = createAdmin();
        ClubResponse club = clubService.create(request("Deact " + System.nanoTime()), admin);

        mockMvc.perform(post("/api/clubs/{id}/deactivate", club.id())
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/clubs")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == %d)]".formatted(club.id())).doesNotExist());
    }

    @Test
    void studentJoinReturns201AndLeaveReturns200() throws Exception {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Join " + System.nanoTime()), admin);

        mockMvc.perform(post("/api/clubs/{id}/membership", club.id())
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clubId").value(club.id()))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(delete("/api/clubs/{id}/membership", club.id())
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LEFT"));
    }

    @Test
    void joinInactiveClubReturns409() throws Exception {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Inactive " + System.nanoTime()), admin);
        clubService.deactivate(club.id(), admin);

        mockMvc.perform(post("/api/clubs/{id}/membership", club.id())
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLUB_INACTIVE"));
    }

    @Test
    void leaveWithoutMembershipReturns404() throws Exception {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("NoMember " + System.nanoTime()), admin);

        mockMvc.perform(delete("/api/clubs/{id}/membership", club.id())
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
    }

    @Test
    void rosterForAdminReturnsMembersStudentForbidden() throws Exception {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Roster " + System.nanoTime()), admin);
        clubService.join(club.id(), student);

        mockMvc.perform(get("/api/clubs/{id}/members", club.id())
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].studentId").value(student))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].rollNumber").exists());

        mockMvc.perform(get("/api/clubs/{id}/members", club.id())
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
