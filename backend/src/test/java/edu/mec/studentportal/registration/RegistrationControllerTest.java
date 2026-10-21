package edu.mec.studentportal.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.mec.studentportal.testsupport.TestAuthSecurityConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** HTTP contract for the registration endpoints: status codes, error codes and JSON shape. */
@EnabledIf(
    value = "edu.mec.studentportal.registration.RegistrationTestDatabase#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class RegistrationControllerTest extends RegistrationIntegrationTestSupport {
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void registerWithoutAuthenticationReturns401() throws Exception {
        long event = createApprovedEvent(createOrganizer(), 5);

        mockMvc.perform(post("/api/events/{eventId}/registrations", event))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void registerReturnsCreatedTicketWithSeatCounts() throws Exception {
        long student = createStudent();
        long event = createApprovedEvent(createOrganizer(), 5);

        mockMvc.perform(post("/api/events/{eventId}/registrations", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventId").value(event))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.ticketNo").value(startsWith("EVT-" + event + "-")))
            .andExpect(jsonPath("$.registrationId").isNumber())
            .andExpect(jsonPath("$.registeredAt").exists())
            .andExpect(jsonPath("$.registeredCount").value(1))
            .andExpect(jsonPath("$.availableSlots").value(4));
    }

    @Test
    void duplicateRegisterReturns409WithStableCode() throws Exception {
        long student = createStudent();
        long event = createApprovedEvent(createOrganizer(), 5);
        registrationService.register(event, student);

        mockMvc.perform(post("/api/events/{eventId}/registrations", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_REGISTRATION"))
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void fullEventReturns409EventFull() throws Exception {
        long event = createApprovedEvent(createOrganizer(), 1);
        long first = createStudent();
        long second = createStudent();
        registrationService.register(event, first);

        mockMvc.perform(post("/api/events/{eventId}/registrations", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, second)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_FULL"));
    }

    @Test
    void cancelEndpointOnlyCancelsTheCallersOwnRegistration() throws Exception {
        long studentA = createStudent();
        long studentB = createStudent();
        long event = createApprovedEvent(createOrganizer(), 5);
        registrationService.register(event, studentA);
        registrationService.register(event, studentB);

        mockMvc.perform(delete("/api/events/{eventId}/registrations/me", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, studentB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.registeredCount").value(1));

        assertThat(statusOf(studentA, event)).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(statusOf(studentB, event)).isEqualTo(RegistrationStatus.CANCELLED);

        mockMvc.perform(delete("/api/events/{eventId}/registrations/me", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, studentB))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REGISTRATION_ALREADY_CANCELLED"));
    }

    @Test
    void listOwnHistoryReturnsBothStates() throws Exception {
        long student = createStudent();
        long eventA = createApprovedEvent(createOrganizer(), 5);
        long eventB = createApprovedEvent(createOrganizer(), 5);
        registrationService.register(eventA, student);
        registrationService.register(eventB, student);
        registrationService.cancel(eventB, student);

        String body = mockMvc.perform(get("/api/registrations/me")
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andReturn().getResponse().getContentAsString();

        List<RegistrationResponse> history = objectMapper.readValue(
            body,
            new TypeReference<List<RegistrationResponse>>() {}
        );
        assertThat(history).extracting(RegistrationResponse::eventId)
            .containsExactlyInAnyOrder(eventA, eventB);
    }

    @Test
    void rosterEnforcesOwnerAndStaffAccess() throws Exception {
        long owner = createOrganizer();
        long student = createStudent();
        long attendee = createStudent();
        long event = createApprovedEvent(owner, 5);
        registrationService.register(event, attendee);

        mockMvc.perform(get("/api/events/{eventId}/registrations", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capacity").value(5))
            .andExpect(jsonPath("$.registeredCount").value(1))
            .andExpect(jsonPath("$.availableSlots").value(4))
            .andExpect(jsonPath("$.registrations[0].studentId").value(attendee))
            .andExpect(jsonPath("$.registrations[0].rollNumber").isString())
            .andExpect(jsonPath("$.registrations[0].fullName").isString());

        mockMvc.perform(get("/api/events/{eventId}/registrations", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void errorBodyFollowsSharedContract() throws Exception {
        long student = createStudent();
        long event = createEvent(createOrganizer(), "PENDING_APPROVAL", now().plusHours(1), 5);

        mockMvc.perform(post("/api/events/{eventId}/registrations", event)
                .header(TestAuthSecurityConfig.TEST_AUTH_HEADER, student)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("EVENT_NOT_APPROVED"))
            .andExpect(jsonPath("$.message").isString())
            .andExpect(jsonPath("$.path").value("/api/events/" + event + "/registrations"));
    }
}
