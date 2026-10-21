package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiErrorResponseWriter;
import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.config.BootstrapSecurityConfig;
import edu.mec.studentportal.security.JwtTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminUserController.class)
@Import({BootstrapSecurityConfig.class, ApiErrorResponseWriter.class})
class AdminUserControllerTest {
    private static final String ADMIN_TOKEN = "admin-token";
    private static final long ADMIN_ID = 99L;
    private static final String STUDENT_TOKEN = "student-token";
    private static final long STUDENT_ID = 41L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    void returnsPaginatedUsersWithRoleSpecificProfile() throws Exception {
        authenticateAsAdmin();
        when(userService.listUsers(defaultQuery())).thenReturn(
            new AdminUserPageResponse(
                List.of(new AdminUserResponse(
                    41L,
                    "Ayesha Khan",
                    "ayesha@example.edu",
                    "Computing",
                    Role.STUDENT,
                    true,
                    new StudentProfile("R2026001", 3, "2026")
                )),
                0,
                20,
                1,
                1
            )
        );

        mockMvc.perform(get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.items[0].id").value(41))
            .andExpect(jsonPath("$.items[0].fullName").value("Ayesha Khan"))
            .andExpect(jsonPath("$.items[0].email").value(
                "ayesha@example.edu"
            ))
            .andExpect(jsonPath("$.items[0].department").value("Computing"))
            .andExpect(jsonPath("$.items[0].role").value("STUDENT"))
            .andExpect(jsonPath("$.items[0].enabled").value(true))
            .andExpect(jsonPath("$.items[0].profile.rollNumber").value(
                "R2026001"
            ))
            .andExpect(jsonPath("$.items[0].profile.semester").value(3))
            .andExpect(jsonPath("$.items[0].profile.batch").value("2026"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
            .andExpect(content().string(not(containsString("passwordHash"))));

        verify(userService).listUsers(defaultQuery());
    }

    @Test
    void forwardsSupportedFiltersAndPagination() throws Exception {
        authenticateAsAdmin();
        AdminUserListQuery query = new AdminUserListQuery(
            2,
            5,
            Role.STUDENT,
            true,
            "Ayesha"
        );
        when(userService.listUsers(query)).thenReturn(
            new AdminUserPageResponse(List.of(), 2, 5, 12, 3)
        );

        mockMvc.perform(get(
                "/api/admin/users"
                    + "?page=2&size=5&role=STUDENT&enabled=true&q=Ayesha"
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.size").value(5))
            .andExpect(jsonPath("$.totalItems").value(12))
            .andExpect(jsonPath("$.totalPages").value(3));

        verify(userService).listUsers(query);
    }

    @Test
    void emptyResultReportsZeroTotalPages() throws Exception {
        authenticateAsAdmin();
        when(userService.listUsers(defaultQuery())).thenReturn(
            new AdminUserPageResponse(List.of(), 0, 20, 0, 0)
        );

        mockMvc.perform(get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.totalItems").value(0))
            .andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "page=-1",
        "page=abc",
        "size=0",
        "size=101",
        "size=abc",
        "role=TEACHER",
        "enabled=maybe"
    })
    void rejectsInvalidQueryParameters(String queryString) throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(get("/api/admin/users?" + queryString)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ))
            .andExpect(jsonPath("$.path").value("/api/admin/users"));

        verify(userService, never()).listUsers(any(AdminUserListQuery.class));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.UNAUTHENTICATED
            ))
            .andExpect(jsonPath("$.path").value("/api/admin/users"));

        verifyNoInteractions(userService, jwtTokenService);
    }

    @Test
    void rejectsAuthenticatedNonAdmin() throws Exception {
        when(jwtTokenService.decodeToken(STUDENT_TOKEN))
            .thenReturn(token(STUDENT_TOKEN, STUDENT_ID, Role.STUDENT));
        when(userService.findSecurityStateById(STUDENT_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(STUDENT_ID, Role.STUDENT, true)
            ));

        mockMvc.perform(get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.FORBIDDEN))
            .andExpect(jsonPath("$.path").value("/api/admin/users"));

        verify(userService, never()).listUsers(any(AdminUserListQuery.class));
    }

    @Test
    void inconsistentStoredRoleProfileReturnsInternalError() throws Exception {
        authenticateAsAdmin();
        when(userService.listUsers(any(AdminUserListQuery.class))).thenThrow(
            new IllegalStateException("Role/profile mismatch for user 7")
        );

        mockMvc.perform(get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.INTERNAL_ERROR))
            .andExpect(jsonPath("$.message").value(
                "An unexpected server error occurred."
            ))
            .andExpect(content().string(
                not(containsString("Role/profile mismatch"))
            ))
            .andExpect(jsonPath("$.path").value("/api/admin/users"));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void createsAccountForEveryRole(Role role) throws Exception {
        authenticateAsAdmin();
        when(userService.createUser(any(AdminUserCreateCommand.class)))
            .thenAnswer(invocation -> {
                AdminUserCreateCommand command = invocation.getArgument(0);
                return new AdminUserResponse(
                    51L,
                    command.fullName(),
                    command.email(),
                    command.department(),
                    command.role(),
                    true,
                    command.profile()
                );
            });

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(role)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(51))
            .andExpect(jsonPath("$.fullName").value("Ayesha Khan"))
            .andExpect(jsonPath("$.email").value("ayesha@example.edu"))
            .andExpect(jsonPath("$.department").value("Computing"))
            .andExpect(jsonPath("$.role").value(role.name()))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(content().string(not(containsString("password"))));

        ArgumentCaptor<AdminUserCreateCommand> captor =
            ArgumentCaptor.forClass(AdminUserCreateCommand.class);
        verify(userService).createUser(captor.capture());
        assertThat(captor.getValue())
            .usingRecursiveComparison()
            .isEqualTo(new AdminUserCreateCommand(
                "Ayesha Khan",
                "ayesha@example.edu",
                "initial-password",
                "Computing",
                role,
                expectedProfile(role)
            ));
    }

    @Test
    void rejectsUnknownTopLevelProperty() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(Role.STUDENT).replace(
                    "\"role\": \"STUDENT\"",
                    "\"role\": \"STUDENT\", \"enabled\": false"
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).createUser(any());
    }

    @Test
    void rejectsUnsupportedProfileProperty() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fullName": "Ayesha Khan",
                      "email": "ayesha@example.edu",
                      "password": "initial-password",
                      "department": "Computing",
                      "role": "STUDENT",
                      "profile": {
                        "rollNumber": "R2026001",
                        "semester": 3,
                        "batch": "2026",
                        "employeeId": "E1001"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).createUser(any());
    }

    @Test
    void rejectsProfileFieldsFromAnotherRole() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fullName": "Ayesha Khan",
                      "email": "ayesha@example.edu",
                      "password": "initial-password",
                      "department": "Computing",
                      "role": "ORGANIZER",
                      "profile": {
                        "rollNumber": "R2026001",
                        "semester": 3,
                        "batch": "2026"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).createUser(any());
    }

    @Test
    void rejectsUnknownRole() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(Role.STUDENT).replace(
                    "\"role\": \"STUDENT\"",
                    "\"role\": \"TEACHER\""
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).createUser(any());
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(Role.STUDENT).replace(
                    "\"Ayesha@Example.EDU\"",
                    "\"not-an-email\""
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).createUser(any());
    }

    @Test
    void rejectsPasswordOutsideByteLimits() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(Role.STUDENT).replace(
                    "\"initial-password\"",
                    "\"short\""
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).createUser(any());
    }

    @Test
    void rejectsMissingProfile() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fullName": "Ayesha Khan",
                      "email": "ayesha@example.edu",
                      "password": "initial-password",
                      "department": "Computing",
                      "role": "STUDENT"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).createUser(any());
    }

    @Test
    void reportsDuplicateValuesAsConflict() throws Exception {
        authenticateAsAdmin();
        when(userService.createUser(any(AdminUserCreateCommand.class)))
            .thenThrow(new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCodes.CONFLICT,
                "A user with the same email or profile identifier "
                    + "already exists."
            ));

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(Role.STUDENT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.CONFLICT))
            .andExpect(jsonPath("$.path").value("/api/admin/users"))
            .andExpect(content().string(
                not(containsString("password"))
            ));
    }

    @Test
    void requiresAuthenticationForCreate() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(Role.STUDENT)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.UNAUTHENTICATED
            ))
            .andExpect(jsonPath("$.path").value("/api/admin/users"));

        verifyNoInteractions(userService, jwtTokenService);
    }

    @Test
    void rejectsAuthenticatedNonAdminForCreate() throws Exception {
        when(jwtTokenService.decodeToken(STUDENT_TOKEN))
            .thenReturn(token(STUDENT_TOKEN, STUDENT_ID, Role.STUDENT));
        when(userService.findSecurityStateById(STUDENT_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(STUDENT_ID, Role.STUDENT, true)
            ));

        mockMvc.perform(post("/api/admin/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + STUDENT_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(Role.STUDENT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.FORBIDDEN))
            .andExpect(jsonPath("$.path").value("/api/admin/users"));

        verify(userService, never()).createUser(any());
    }

    @Test
    void updatesUserAndReturnsRepresentation() throws Exception {
        authenticateAsAdmin();
        when(userService.updateUser(
            eq(41L),
            eq(ADMIN_ID),
            any(AdminUserUpdateCommand.class)
        )).thenReturn(new AdminUserResponse(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.STUDENT,
            true,
            new StudentProfile("R2026001", 3, "2026")
        ));

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(41))
            .andExpect(jsonPath("$.fullName").value("Ayesha Khan"))
            .andExpect(jsonPath("$.email").value("ayesha@example.edu"))
            .andExpect(jsonPath("$.department").value("Computing"))
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.profile.rollNumber").value(
                "R2026001"
            ))
            .andExpect(jsonPath("$.profile.semester").value(3))
            .andExpect(jsonPath("$.profile.batch").value("2026"))
            .andExpect(content().string(not(containsString("password"))));

        ArgumentCaptor<AdminUserUpdateCommand> captor =
            ArgumentCaptor.forClass(AdminUserUpdateCommand.class);
        verify(userService).updateUser(
            eq(41L),
            eq(ADMIN_ID),
            captor.capture()
        );
        assertThat(captor.getValue())
            .usingRecursiveComparison()
            .isEqualTo(new AdminUserUpdateCommand(
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                Role.STUDENT,
                new StudentProfile("R2026001", 3, "2026")
            ));
    }

    @Test
    void updatesRoleAndReplacesProfile() throws Exception {
        authenticateAsAdmin();
        when(userService.updateUser(
            eq(41L),
            eq(ADMIN_ID),
            any(AdminUserUpdateCommand.class)
        )).thenReturn(new AdminUserResponse(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.FACULTY,
            true,
            new FacultyProfile("E1001", "Professor")
        ));

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.FACULTY)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("FACULTY"))
            .andExpect(jsonPath("$.profile.employeeId").value("E1001"))
            .andExpect(jsonPath("$.profile.designation").value(
                "Professor"
            ));

        ArgumentCaptor<AdminUserUpdateCommand> captor =
            ArgumentCaptor.forClass(AdminUserUpdateCommand.class);
        verify(userService).updateUser(
            eq(41L),
            eq(ADMIN_ID),
            captor.capture()
        );
        assertThat(captor.getValue().role()).isEqualTo(Role.FACULTY);
        assertThat(captor.getValue().profile())
            .usingRecursiveComparison()
            .isEqualTo(new FacultyProfile("E1001", "Professor"));
    }

    @Test
    void rejectsPasswordFieldOnUpdate() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT).replace(
                    "\"role\": \"STUDENT\"",
                    "\"role\": \"STUDENT\", \"password\": \"initial-password\""
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).updateUser(anyLong(), anyLong(), any());
    }

    @Test
    void rejectsEnabledFieldOnUpdate() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT).replace(
                    "\"role\": \"STUDENT\"",
                    "\"role\": \"STUDENT\", \"enabled\": false"
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).updateUser(anyLong(), anyLong(), any());
    }

    @Test
    void rejectsUnsupportedTopLevelFieldOnUpdate() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT).replace(
                    "\"role\": \"STUDENT\"",
                    "\"role\": \"STUDENT\", \"nickname\": \"Ash\""
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).updateUser(anyLong(), anyLong(), any());
    }

    @Test
    void rejectsUnsupportedProfileFieldOnUpdate() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT).replace(
                    "\"batch\": \"2026\"",
                    "\"batch\": \"2026\", \"employeeId\": \"E1001\""
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).updateUser(anyLong(), anyLong(), any());
    }

    @Test
    void rejectsMissingUpdateFields() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fullName": "Ayesha Khan",
                      "email": "ayesha@example.edu",
                      "department": "Computing",
                      "role": "STUDENT"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).updateUser(anyLong(), anyLong(), any());
    }

    @Test
    void missingUserReturnsNotFoundOnUpdate() throws Exception {
        authenticateAsAdmin();
        when(userService.updateUser(
            eq(41L),
            eq(ADMIN_ID),
            any(AdminUserUpdateCommand.class)
        )).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCodes.NOT_FOUND,
            "User was not found."
        ));

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.NOT_FOUND))
            .andExpect(jsonPath("$.path").value("/api/admin/users/41"));
    }

    @Test
    void duplicateValuesReturnConflictOnUpdate() throws Exception {
        authenticateAsAdmin();
        when(userService.updateUser(
            eq(41L),
            eq(ADMIN_ID),
            any(AdminUserUpdateCommand.class)
        )).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCodes.CONFLICT,
            "A user with the same email or profile identifier "
                + "already exists."
        ));

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.CONFLICT));
    }

    @Test
    void rejectsMalformedUserId() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(put("/api/admin/users/not-a-number")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ));

        verify(userService, never()).updateUser(anyLong(), anyLong(), any());
    }

    @Test
    void requiresAuthenticationForUpdate() throws Exception {
        mockMvc.perform(put("/api/admin/users/41")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.UNAUTHENTICATED
            ))
            .andExpect(jsonPath("$.path").value("/api/admin/users/41"));

        verifyNoInteractions(userService, jwtTokenService);
    }

    @Test
    void rejectsAuthenticatedNonAdminForUpdate() throws Exception {
        when(jwtTokenService.decodeToken(STUDENT_TOKEN))
            .thenReturn(token(STUDENT_TOKEN, STUDENT_ID, Role.STUDENT));
        when(userService.findSecurityStateById(STUDENT_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(STUDENT_ID, Role.STUDENT, true)
            ));

        mockMvc.perform(put("/api/admin/users/41")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + STUDENT_TOKEN
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(Role.STUDENT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.FORBIDDEN));

        verify(userService, never()).updateUser(anyLong(), anyLong(), any());
    }

    @Test
    void disablesUserAndReturnsDisabledRepresentation() throws Exception {
        authenticateAsAdmin();
        when(userService.disableUser(41L, ADMIN_ID)).thenReturn(
            new AdminUserResponse(
                41L,
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                Role.STUDENT,
                false,
                new StudentProfile("R2026001", 3, "2026")
            )
        );

        mockMvc.perform(post("/api/admin/users/41/disable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.id").value(41))
            .andExpect(jsonPath("$.fullName").value("Ayesha Khan"))
            .andExpect(jsonPath("$.email").value("ayesha@example.edu"))
            .andExpect(jsonPath("$.department").value("Computing"))
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.profile.rollNumber").value(
                "R2026001"
            ))
            .andExpect(jsonPath("$.profile.semester").value(3))
            .andExpect(jsonPath("$.profile.batch").value("2026"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(content().string(not(containsString("password"))));

        verify(userService).disableUser(41L, ADMIN_ID);
    }

    @Test
    void selfDisableReturnsConflict() throws Exception {
        authenticateAsAdmin();
        when(userService.disableUser(ADMIN_ID, ADMIN_ID)).thenThrow(
            new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCodes.CONFLICT,
                "An administrator cannot disable their own account."
            )
        );

        mockMvc.perform(post("/api/admin/users/" + ADMIN_ID + "/disable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.CONFLICT))
            .andExpect(jsonPath("$.path").value(
                "/api/admin/users/" + ADMIN_ID + "/disable"
            ));
    }

    @Test
    void disablingLastEnabledAdminReturnsConflict() throws Exception {
        authenticateAsAdmin();
        when(userService.disableUser(41L, ADMIN_ID)).thenThrow(
            new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCodes.CONFLICT,
                "The last enabled administrator cannot be disabled."
            )
        );

        mockMvc.perform(post("/api/admin/users/41/disable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.CONFLICT));
    }

    @Test
    void missingUserReturnsNotFoundOnDisable() throws Exception {
        authenticateAsAdmin();
        when(userService.disableUser(41L, ADMIN_ID)).thenThrow(
            new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCodes.NOT_FOUND,
                "User was not found."
            )
        );

        mockMvc.perform(post("/api/admin/users/41/disable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.NOT_FOUND))
            .andExpect(jsonPath("$.path").value(
                "/api/admin/users/41/disable"
            ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "0", "-1"})
    void rejectsInvalidUserIdOnDisable(String userId) throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users/" + userId + "/disable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ))
            .andExpect(jsonPath("$.path").value(
                "/api/admin/users/" + userId + "/disable"
            ));

        verify(userService, never()).disableUser(anyLong(), anyLong());
    }

    @Test
    void requiresAuthenticationForDisable() throws Exception {
        mockMvc.perform(post("/api/admin/users/41/disable"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.UNAUTHENTICATED
            ))
            .andExpect(jsonPath("$.path").value(
                "/api/admin/users/41/disable"
            ));

        verifyNoInteractions(userService, jwtTokenService);
    }

    @Test
    void rejectsAuthenticatedNonAdminForDisable() throws Exception {
        when(jwtTokenService.decodeToken(STUDENT_TOKEN))
            .thenReturn(token(STUDENT_TOKEN, STUDENT_ID, Role.STUDENT));
        when(userService.findSecurityStateById(STUDENT_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(STUDENT_ID, Role.STUDENT, true)
            ));

        mockMvc.perform(post("/api/admin/users/41/disable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + STUDENT_TOKEN
                ))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.FORBIDDEN));

        verify(userService, never()).disableUser(anyLong(), anyLong());
    }

    @Test
    void enablesUserAndReturnsEnabledRepresentation() throws Exception {
        authenticateAsAdmin();
        when(userService.enableUser(41L)).thenReturn(
            new AdminUserResponse(
                41L,
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                Role.STUDENT,
                true,
                new StudentProfile("R2026001", 3, "2026")
            )
        );

        mockMvc.perform(post("/api/admin/users/41/enable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.id").value(41))
            .andExpect(jsonPath("$.fullName").value("Ayesha Khan"))
            .andExpect(jsonPath("$.email").value("ayesha@example.edu"))
            .andExpect(jsonPath("$.department").value("Computing"))
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.profile.rollNumber").value(
                "R2026001"
            ))
            .andExpect(jsonPath("$.profile.semester").value(3))
            .andExpect(jsonPath("$.profile.batch").value("2026"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(content().string(not(containsString("password"))));

        verify(userService).enableUser(41L);
    }

    @Test
    void alreadyEnabledUserStillReturnsSuccess() throws Exception {
        authenticateAsAdmin();
        when(userService.enableUser(41L)).thenReturn(
            new AdminUserResponse(
                41L,
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                Role.ORGANIZER,
                true,
                new OrganizerProfile("Club Coordinator")
            )
        );

        mockMvc.perform(post("/api/admin/users/41/enable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.role").value("ORGANIZER"))
            .andExpect(jsonPath("$.profile.designation").value(
                "Club Coordinator"
            ));

        verify(userService).enableUser(41L);
    }

    @Test
    void missingUserReturnsNotFoundOnEnable() throws Exception {
        authenticateAsAdmin();
        when(userService.enableUser(41L)).thenThrow(
            new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCodes.NOT_FOUND,
                "User was not found."
            )
        );

        mockMvc.perform(post("/api/admin/users/41/enable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.NOT_FOUND))
            .andExpect(jsonPath("$.path").value(
                "/api/admin/users/41/enable"
            ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "0", "-1"})
    void rejectsInvalidUserIdOnEnable(String userId) throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users/" + userId + "/enable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INVALID_REQUEST
            ))
            .andExpect(jsonPath("$.path").value(
                "/api/admin/users/" + userId + "/enable"
            ));

        verify(userService, never()).enableUser(anyLong());
    }

    @Test
    void requiresAuthenticationForEnable() throws Exception {
        mockMvc.perform(post("/api/admin/users/41/enable"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.UNAUTHENTICATED
            ))
            .andExpect(jsonPath("$.path").value(
                "/api/admin/users/41/enable"
            ));

        verifyNoInteractions(userService, jwtTokenService);
    }

    @Test
    void rejectsAuthenticatedNonAdminForEnable() throws Exception {
        when(jwtTokenService.decodeToken(STUDENT_TOKEN))
            .thenReturn(token(STUDENT_TOKEN, STUDENT_ID, Role.STUDENT));
        when(userService.findSecurityStateById(STUDENT_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(STUDENT_ID, Role.STUDENT, true)
            ));

        mockMvc.perform(post("/api/admin/users/41/enable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + STUDENT_TOKEN
                ))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value(ApiErrorCodes.FORBIDDEN));

        verify(userService, never()).enableUser(anyLong());
    }

    @Test
    void unexpectedFailureReturnsInternalErrorOnEnable() throws Exception {
        authenticateAsAdmin();
        when(userService.enableUser(41L)).thenThrow(
            new DataAccessResourceFailureException("database unavailable")
        );

        mockMvc.perform(post("/api/admin/users/41/enable")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + ADMIN_TOKEN
                ))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value(
                ApiErrorCodes.INTERNAL_ERROR
            ))
            .andExpect(jsonPath("$.message").value(
                "An unexpected server error occurred."
            ))
            .andExpect(content().string(
                not(containsString("database unavailable"))
            ));
    }

    private static String updateBody(Role role) {
        return switch (role) {
            case STUDENT -> """
                {
                  "fullName": "  Ayesha Khan  ",
                  "email": "Ayesha@Example.EDU",
                  "department": " Computing ",
                  "role": "STUDENT",
                  "profile": {
                    "rollNumber": "R2026001",
                    "semester": 3,
                    "batch": "2026"
                  }
                }
                """;
            case ORGANIZER -> """
                {
                  "fullName": "  Ayesha Khan  ",
                  "email": "Ayesha@Example.EDU",
                  "department": " Computing ",
                  "role": "ORGANIZER",
                  "profile": {"designation": "Club Coordinator"}
                }
                """;
            case FACULTY -> """
                {
                  "fullName": "  Ayesha Khan  ",
                  "email": "Ayesha@Example.EDU",
                  "department": " Computing ",
                  "role": "FACULTY",
                  "profile": {
                    "employeeId": "E1001",
                    "designation": "Professor"
                  }
                }
                """;
            case ADMIN -> """
                {
                  "fullName": "  Ayesha Khan  ",
                  "email": "Ayesha@Example.EDU",
                  "department": " Computing ",
                  "role": "ADMIN",
                  "profile": {"adminLevel": 2}
                }
                """;
        };
    }

    private static String createBody(Role role) {
        return switch (role) {
            case STUDENT -> """
                {
                  "fullName": "  Ayesha Khan  ",
                  "email": "Ayesha@Example.EDU",
                  "password": "initial-password",
                  "department": " Computing ",
                  "role": "STUDENT",
                  "profile": {
                    "rollNumber": "R2026001",
                    "semester": 3,
                    "batch": "2026"
                  }
                }
                """;
            case ORGANIZER -> """
                {
                  "fullName": "  Ayesha Khan  ",
                  "email": "Ayesha@Example.EDU",
                  "password": "initial-password",
                  "department": " Computing ",
                  "role": "ORGANIZER",
                  "profile": {"designation": "Club Coordinator"}
                }
                """;
            case FACULTY -> """
                {
                  "fullName": "  Ayesha Khan  ",
                  "email": "Ayesha@Example.EDU",
                  "password": "initial-password",
                  "department": " Computing ",
                  "role": "FACULTY",
                  "profile": {
                    "employeeId": "E1001",
                    "designation": "Professor"
                  }
                }
                """;
            case ADMIN -> """
                {
                  "fullName": "  Ayesha Khan  ",
                  "email": "Ayesha@Example.EDU",
                  "password": "initial-password",
                  "department": " Computing ",
                  "role": "ADMIN",
                  "profile": {"adminLevel": 2}
                }
                """;
        };
    }

    private static UserProfile expectedProfile(Role role) {
        return switch (role) {
            case STUDENT -> new StudentProfile("R2026001", 3, "2026");
            case ORGANIZER -> new OrganizerProfile("Club Coordinator");
            case FACULTY -> new FacultyProfile("E1001", "Professor");
            case ADMIN -> new AdminProfile(2);
        };
    }

    private void authenticateAsAdmin() {
        when(jwtTokenService.decodeToken(ADMIN_TOKEN))
            .thenReturn(token(ADMIN_TOKEN, ADMIN_ID, Role.ADMIN));
        when(userService.findSecurityStateById(ADMIN_ID))
            .thenReturn(Optional.of(
                new UserSecurityState(ADMIN_ID, Role.ADMIN, true)
            ));
    }

    private static AdminUserListQuery defaultQuery() {
        return new AdminUserListQuery(0, 20, null, null, null);
    }

    private static Jwt token(String value, long userId, Role role) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(value)
            .header("alg", "HS256")
            .subject(Long.toString(userId))
            .claim("role", role.name())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(60))
            .build();
    }
}
