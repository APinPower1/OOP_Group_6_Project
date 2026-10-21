package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserDao userDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void normalizesEmailBeforeLookup() {
        UserAccount account = account();
        when(userDao.findByEmail("alice@example.com"))
            .thenReturn(Optional.of(account));

        assertThat(userService.findByEmail("  Alice@Example.COM  "))
            .contains(account);
        verify(userDao).findByEmail("alice@example.com");
    }

    @Test
    void returnsEmptyForNullEmailWithoutQuerying() {
        assertThat(userService.findByEmail(null)).isEmpty();
        verifyNoInteractions(userDao);
    }

    @Test
    void returnsEmptyForBlankEmailWithoutQuerying() {
        assertThat(userService.findByEmail("   ")).isEmpty();
        verifyNoInteractions(userDao);
    }

    @Test
    void returnsEmptyWhenEmailIsUnknown() {
        when(userDao.findByEmail("nobody@example.com"))
            .thenReturn(Optional.empty());

        assertThat(userService.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void normalizeEmailTrimsAndLowerCases() {
        assertThat(UserService.normalizeEmail(" Alice@Example.COM "))
            .isEqualTo("alice@example.com");
    }

    @Test
    void normalizeEmailRejectsNull() {
        assertThatThrownBy(() -> UserService.normalizeEmail(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("email must not be null");
    }

    @Test
    void rejectsNonPositiveUserIdOnFindById() {
        assertThat(userService.findById(0)).isEmpty();
        assertThat(userService.findById(-1)).isEmpty();
        verifyNoInteractions(userDao);
    }

    @Test
    void delegatesFindById() {
        UserAccount account = account();
        when(userDao.findById(7L)).thenReturn(Optional.of(account));

        assertThat(userService.findById(7L)).contains(account);
    }

    @Test
    void rejectsNonPositiveUserIdOnSecurityStateLookup() {
        assertThat(userService.findSecurityStateById(0)).isEmpty();
        verifyNoInteractions(userDao);
    }

    @Test
    void delegatesSecurityStateLookup() {
        UserSecurityState state = new UserSecurityState(
            7L,
            Role.STUDENT,
            true
        );
        when(userDao.findSecurityStateById(7L)).thenReturn(Optional.of(state));

        assertThat(userService.findSecurityStateById(7L)).contains(state);
    }

    @Test
    void listsUsersAndComputesTotalPages() {
        AdminUserListQuery query = new AdminUserListQuery(
            1,
            20,
            Role.STUDENT,
            true,
            "Ayesha"
        );
        AdminUserRow row = studentRow();
        when(userDao.searchAdminUsers(query))
            .thenReturn(new AdminUserSearchResult(List.of(row), 21));

        AdminUserPageResponse response = userService.listUsers(query);

        assertThat(response.items())
            .usingRecursiveComparison()
            .isEqualTo(List.of(
                new AdminUserResponse(
                    7L,
                    "Ayesha Khan",
                    "ayesha@example.com",
                    "Computing",
                    Role.STUDENT,
                    true,
                    new StudentProfile("R2026001", 3, "2026")
                )
            ));
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalItems()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void emptyUserListHasZeroTotalPages() {
        AdminUserListQuery query = new AdminUserListQuery(
            0,
            20,
            null,
            null,
            null
        );
        when(userDao.searchAdminUsers(query))
            .thenReturn(new AdminUserSearchResult(List.of(), 0));

        AdminUserPageResponse response = userService.listUsers(query);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void listResponseDoesNotExposePasswordData() throws Exception {
        AdminUserListQuery query = new AdminUserListQuery(
            0,
            20,
            null,
            null,
            null
        );
        when(userDao.searchAdminUsers(query)).thenReturn(
            new AdminUserSearchResult(List.of(studentRow()), 1)
        );

        String json = new ObjectMapper().writeValueAsString(
            userService.listUsers(query)
        );

        assertThat(json).doesNotContain("password");
    }

    @Test
    void createsUserWithHashedPasswordAndEnabledAccount() {
        AdminUserCreateCommand command = createCommand();
        when(passwordEncoder.encode("initial-password"))
            .thenReturn("$2a$10$hashed");
        when(userDao.insertUser(
            "Ayesha Khan",
            "ayesha@example.edu",
            "$2a$10$hashed",
            "Computing",
            Role.STUDENT
        )).thenReturn(51L);

        AdminUserResponse response = userService.createUser(command);

        assertThat(response)
            .usingRecursiveComparison()
            .isEqualTo(new AdminUserResponse(
                51L,
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                Role.STUDENT,
                true,
                new StudentProfile("R2026001", 3, "2026")
            ));
        verify(userDao).insertProfile(51L, command.profile());
    }

    @Test
    void createResponseDoesNotExposePasswordData() throws Exception {
        AdminUserCreateCommand command = createCommand();
        when(passwordEncoder.encode("initial-password"))
            .thenReturn("$2a$10$hashed");
        when(userDao.insertUser(
            "Ayesha Khan",
            "ayesha@example.edu",
            "$2a$10$hashed",
            "Computing",
            Role.STUDENT
        )).thenReturn(51L);

        String json = new ObjectMapper().writeValueAsString(
            userService.createUser(command)
        );

        assertThat(json).doesNotContain("password");
    }

    @Test
    void mapsDuplicateEmailToConflict() {
        AdminUserCreateCommand command = createCommand();
        when(passwordEncoder.encode("initial-password"))
            .thenReturn("$2a$10$hashed");
        when(userDao.insertUser(
            "Ayesha Khan",
            "ayesha@example.edu",
            "$2a$10$hashed",
            "Computing",
            Role.STUDENT
        )).thenThrow(new DuplicateKeyException("duplicate email"));

        assertThatThrownBy(() -> userService.createUser(command))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
                assertThat(exception.getCode())
                    .isEqualTo(ApiErrorCodes.CONFLICT);
            });
        verify(userDao, never()).insertProfile(51L, command.profile());
    }

    @Test
    void mapsDuplicateProfileValueToConflict() {
        AdminUserCreateCommand command = createCommand();
        when(passwordEncoder.encode("initial-password"))
            .thenReturn("$2a$10$hashed");
        when(userDao.insertUser(
            "Ayesha Khan",
            "ayesha@example.edu",
            "$2a$10$hashed",
            "Computing",
            Role.STUDENT
        )).thenReturn(51L);
        doThrow(new DuplicateKeyException("duplicate roll number"))
            .when(userDao)
            .insertProfile(51L, command.profile());

        assertThatThrownBy(() -> userService.createUser(command))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
                assertThat(exception.getCode())
                    .isEqualTo(ApiErrorCodes.CONFLICT);
            });
    }

    @Test
    void rejectsNullCreateCommand() {
        assertThatThrownBy(() -> userService.createUser(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("command must not be null");
        verifyNoInteractions(userDao, passwordEncoder);
    }

    @Test
    void createUserIsTransactional() throws Exception {
        assertThat(UserService.class
            .getMethod("createUser", AdminUserCreateCommand.class)
            .isAnnotationPresent(Transactional.class))
            .isTrue();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void updatesProfileInPlaceWhenRoleIsUnchanged(Role role) {
        AdminUserRow existing = rowFor(role, true);
        AdminUserUpdateCommand command = updateCommand(role);
        when(userDao.lockEnabledAdministratorIds()).thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.updateUser(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            role
        )).thenReturn(1);

        AdminUserResponse response =
            userService.updateUser(41L, 99L, command);

        assertThat(response)
            .usingRecursiveComparison()
            .isEqualTo(new AdminUserResponse(
                41L,
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                role,
                true,
                command.profile()
            ));
        verify(userDao).updateProfile(41L, command.profile());
        verify(userDao, never()).deleteProfile(anyLong(), any());
        verify(userDao, never()).insertProfile(anyLong(), any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void preservesDisabledStatusAndPasswordOnUpdate() {
        AdminUserRow existing = rowFor(Role.ORGANIZER, false);
        AdminUserUpdateCommand command = updateCommand(Role.ORGANIZER);
        when(userDao.lockEnabledAdministratorIds()).thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.updateUser(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.ORGANIZER
        )).thenReturn(1);

        AdminUserResponse response =
            userService.updateUser(41L, 99L, command);

        assertThat(response.enabled()).isFalse();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void replacesProfileWhenRoleChanges() {
        AdminUserRow existing = rowFor(Role.STUDENT, true);
        AdminUserUpdateCommand command = updateCommand(Role.FACULTY);
        when(userDao.lockEnabledAdministratorIds()).thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.hasStudentHistory(41L)).thenReturn(false);
        when(userDao.updateUser(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.FACULTY
        )).thenReturn(1);

        AdminUserResponse response =
            userService.updateUser(41L, 99L, command);

        assertThat(response.role()).isEqualTo(Role.FACULTY);
        assertThat(response.profile())
            .usingRecursiveComparison()
            .isEqualTo(command.profile());
        verify(userDao).deleteProfile(41L, Role.STUDENT);
        verify(userDao).insertProfile(41L, command.profile());
        verify(userDao, never()).updateProfile(anyLong(), any());
    }

    @Test
    void rejectsRoleChangeFromStudentWithHistory() {
        AdminUserRow existing = rowFor(Role.STUDENT, true);
        AdminUserUpdateCommand command = updateCommand(Role.FACULTY);
        when(userDao.lockEnabledAdministratorIds()).thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.hasStudentHistory(41L)).thenReturn(true);

        assertThatThrownBy(
            () -> userService.updateUser(41L, 99L, command)
        ).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode())
                .isEqualTo(ApiErrorCodes.CONFLICT);
        });

        verify(userDao, never()).updateUser(
            anyLong(),
            any(),
            any(),
            any(),
            any()
        );
        verify(userDao, never()).deleteProfile(anyLong(), any());
        verify(userDao, never()).insertProfile(anyLong(), any());
    }

    @Test
    void returnsNotFoundForMissingUser() {
        when(userDao.lockEnabledAdministratorIds()).thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(
            41L,
            99L,
            updateCommand(Role.STUDENT)
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(exception.getCode())
                .isEqualTo(ApiErrorCodes.NOT_FOUND);
        });

        verify(userDao, never()).updateUser(
            anyLong(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void mapsDuplicateEmailToConflictOnUpdate() {
        AdminUserRow existing = rowFor(Role.ORGANIZER, true);
        AdminUserUpdateCommand command = updateCommand(Role.ORGANIZER);
        when(userDao.lockEnabledAdministratorIds()).thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.updateUser(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.ORGANIZER
        )).thenThrow(new DuplicateKeyException("duplicate email"));

        assertThatThrownBy(
            () -> userService.updateUser(41L, 99L, command)
        ).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode())
                .isEqualTo(ApiErrorCodes.CONFLICT);
        });

        verify(userDao, never()).updateProfile(anyLong(), any());
    }

    @Test
    void mapsDuplicateProfileValueToConflictOnUpdate() {
        AdminUserRow existing = rowFor(Role.ORGANIZER, true);
        AdminUserUpdateCommand command = updateCommand(Role.ORGANIZER);
        when(userDao.lockEnabledAdministratorIds()).thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.updateUser(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.ORGANIZER
        )).thenReturn(1);
        doThrow(new DuplicateKeyException("duplicate designation"))
            .when(userDao)
            .updateProfile(41L, command.profile());

        assertThatThrownBy(
            () -> userService.updateUser(41L, 99L, command)
        ).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode())
                .isEqualTo(ApiErrorCodes.CONFLICT);
        });
    }

    @Test
    void rejectsSelfDemotion() {
        AdminUserRow existing = rowFor(Role.ADMIN, true);
        AdminUserUpdateCommand command = updateCommand(Role.FACULTY);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(41L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(
            () -> userService.updateUser(41L, 41L, command)
        ).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode())
                .isEqualTo(ApiErrorCodes.CONFLICT);
        });

        verify(userDao, never()).updateUser(
            anyLong(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void rejectsDemotingLastEnabledAdministrator() {
        AdminUserRow existing = rowFor(Role.ADMIN, true);
        AdminUserUpdateCommand command = updateCommand(Role.FACULTY);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(41L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(
            () -> userService.updateUser(41L, 99L, command)
        ).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode())
                .isEqualTo(ApiErrorCodes.CONFLICT);
        });

        verify(userDao, never()).updateUser(
            anyLong(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void allowsDemotionWhenAnotherEnabledAdministratorExists() {
        AdminUserRow existing = rowFor(Role.ADMIN, true);
        AdminUserUpdateCommand command = updateCommand(Role.FACULTY);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(41L, 99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.updateUser(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.FACULTY
        )).thenReturn(1);

        AdminUserResponse response =
            userService.updateUser(41L, 99L, command);

        assertThat(response.role()).isEqualTo(Role.FACULTY);
        verify(userDao).deleteProfile(41L, Role.ADMIN);
        verify(userDao).insertProfile(41L, command.profile());
    }

    @Test
    void propagatesUnexpectedProfileFailure() {
        AdminUserRow existing = rowFor(Role.ORGANIZER, true);
        AdminUserUpdateCommand command = updateCommand(Role.ORGANIZER);
        when(userDao.lockEnabledAdministratorIds()).thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.updateUser(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.ORGANIZER
        )).thenReturn(1);
        doThrow(new IllegalStateException("profile write failed"))
            .when(userDao)
            .updateProfile(41L, command.profile());

        assertThatThrownBy(
            () -> userService.updateUser(41L, 99L, command)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("profile write failed");
    }

    @Test
    void rejectsNullUpdateCommand() {
        assertThatThrownBy(() -> userService.updateUser(
            41L,
            99L,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("command must not be null");
        verifyNoInteractions(userDao, passwordEncoder);
    }

    @Test
    void updateUserIsTransactional() throws Exception {
        assertThat(UserService.class
            .getMethod(
                "updateUser",
                long.class,
                long.class,
                AdminUserUpdateCommand.class
            )
            .isAnnotationPresent(Transactional.class))
            .isTrue();
    }

    @Test
    void disablesRegularUserAndPreservesAccountDetails() {
        AdminUserRow existing = rowFor(Role.STUDENT, true);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.disableUser(41L)).thenReturn(1);

        AdminUserResponse response = userService.disableUser(41L, 99L);

        assertThat(response)
            .usingRecursiveComparison()
            .isEqualTo(new AdminUserResponse(
                41L,
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                Role.STUDENT,
                false,
                new StudentProfile("R2026001", 3, "2026")
            ));
        verify(userDao).disableUser(41L);
        verify(userDao, never()).updateUser(
            anyLong(),
            any(),
            any(),
            any(),
            any()
        );
        verify(userDao, never()).updateProfile(anyLong(), any());
        verify(userDao, never()).deleteProfile(anyLong(), any());
        verify(userDao, never()).insertProfile(anyLong(), any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void disableResponseDoesNotExposePasswordData() throws Exception {
        AdminUserRow existing = rowFor(Role.STUDENT, true);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.disableUser(41L)).thenReturn(1);

        String json = new ObjectMapper().writeValueAsString(
            userService.disableUser(41L, 99L)
        );

        assertThat(json).doesNotContain("password");
    }

    @Test
    void rejectsSelfDisable() {
        AdminUserRow existing = rowFor(Role.ADMIN, true);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(41L, 99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.disableUser(41L, 41L))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
                assertThat(exception.getCode())
                    .isEqualTo(ApiErrorCodes.CONFLICT);
                assertThat(exception.getMessage())
                    .isEqualTo(
                        "An administrator cannot disable their own account."
                    );
            });

        verify(userDao, never()).disableUser(anyLong());
    }

    @Test
    void rejectsDisablingLastEnabledAdministrator() {
        AdminUserRow existing = rowFor(Role.ADMIN, true);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(41L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.disableUser(41L, 99L))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
                assertThat(exception.getCode())
                    .isEqualTo(ApiErrorCodes.CONFLICT);
                assertThat(exception.getMessage())
                    .isEqualTo(
                        "The last enabled administrator cannot be disabled."
                    );
            });

        verify(userDao, never()).disableUser(anyLong());
    }

    @Test
    void allowsDisablingAdministratorWhenAnotherEnabledAdministratorExists() {
        AdminUserRow existing = rowFor(Role.ADMIN, true);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(41L, 99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.disableUser(41L)).thenReturn(1);

        AdminUserResponse response = userService.disableUser(41L, 99L);

        assertThat(response.enabled()).isFalse();
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.profile())
            .usingRecursiveComparison()
            .isEqualTo(new AdminProfile(2));
        verify(userDao).disableUser(41L);
        verify(userDao, never()).deleteProfile(anyLong(), any());
    }

    @Test
    void disablingAlreadyDisabledAccountIsIdempotent() {
        AdminUserRow existing = rowFor(Role.STUDENT, false);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));

        AdminUserResponse response = userService.disableUser(41L, 99L);

        assertThat(response)
            .usingRecursiveComparison()
            .isEqualTo(new AdminUserResponse(
                41L,
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                Role.STUDENT,
                false,
                new StudentProfile("R2026001", 3, "2026")
            ));
        verify(userDao, never()).disableUser(anyLong());
    }

    @Test
    void returnsNotFoundForMissingUserOnDisable() {
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.disableUser(41L, 99L))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getCode())
                    .isEqualTo(ApiErrorCodes.NOT_FOUND);
            });

        verify(userDao, never()).disableUser(anyLong());
    }

    @Test
    void failsWhenDisableAffectsNoRow() {
        AdminUserRow existing = rowFor(Role.STUDENT, true);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.disableUser(41L)).thenReturn(0);

        assertThatThrownBy(() -> userService.disableUser(41L, 99L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "User disable did not affect a row for user 41"
            );
    }

    @Test
    void disableUserIsTransactional() throws Exception {
        assertThat(UserService.class
            .getMethod("disableUser", long.class, long.class)
            .isAnnotationPresent(Transactional.class))
            .isTrue();
    }

    @Test
    void enablesDisabledUserAndPreservesAccountDetails() {
        AdminUserRow existing = rowFor(Role.STUDENT, false);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.enableUser(41L)).thenReturn(1);

        AdminUserResponse response = userService.enableUser(41L);

        assertThat(response)
            .usingRecursiveComparison()
            .isEqualTo(new AdminUserResponse(
                41L,
                "Ayesha Khan",
                "ayesha@example.edu",
                "Computing",
                Role.STUDENT,
                true,
                new StudentProfile("R2026001", 3, "2026")
            ));
        verify(userDao).enableUser(41L);
        verify(userDao, never()).updateUser(
            anyLong(),
            any(),
            any(),
            any(),
            any()
        );
        verify(userDao, never()).updateProfile(anyLong(), any());
        verify(userDao, never()).deleteProfile(anyLong(), any());
        verify(userDao, never()).insertProfile(anyLong(), any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void enableResponseDoesNotExposePasswordData() throws Exception {
        AdminUserRow existing = rowFor(Role.STUDENT, false);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.enableUser(41L)).thenReturn(1);

        String json = new ObjectMapper().writeValueAsString(
            userService.enableUser(41L)
        );

        assertThat(json).doesNotContain("password");
    }

    @Test
    void enablingAlreadyEnabledAccountIsIdempotent() {
        AdminUserRow existing = rowFor(Role.ORGANIZER, true);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));

        AdminUserResponse response = userService.enableUser(41L);

        assertThat(response.enabled()).isTrue();
        assertThat(response.role()).isEqualTo(Role.ORGANIZER);
        assertThat(response.profile())
            .usingRecursiveComparison()
            .isEqualTo(new OrganizerProfile("Club Coordinator"));
        verify(userDao, never()).enableUser(anyLong());
    }

    @Test
    void returnsNotFoundForMissingUserOnEnable() {
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of());
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.enableUser(41L))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getCode())
                    .isEqualTo(ApiErrorCodes.NOT_FOUND);
            });

        verify(userDao, never()).enableUser(anyLong());
    }

    @Test
    void failsWhenEnableAffectsNoRow() {
        AdminUserRow existing = rowFor(Role.STUDENT, false);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.enableUser(41L)).thenReturn(0);

        assertThatThrownBy(() -> userService.enableUser(41L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "User enable did not affect a row for user 41"
            );
    }

    @Test
    void propagatesUnexpectedEnableFailure() {
        AdminUserRow existing = rowFor(Role.STUDENT, false);
        when(userDao.lockEnabledAdministratorIds())
            .thenReturn(List.of(99L));
        when(userDao.findAdminUserByIdForUpdate(41L))
            .thenReturn(Optional.of(existing));
        when(userDao.enableUser(41L)).thenThrow(
            new DataAccessResourceFailureException("database unavailable")
        );

        assertThatThrownBy(() -> userService.enableUser(41L))
            .isInstanceOf(DataAccessResourceFailureException.class)
            .hasMessage("database unavailable");
    }

    @Test
    void enableUserIsTransactional() throws Exception {
        assertThat(UserService.class
            .getMethod("enableUser", long.class)
            .isAnnotationPresent(Transactional.class))
            .isTrue();
    }

    private static AdminUserRow rowFor(Role role, boolean enabled) {
        return new AdminUserRow(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            role,
            enabled,
            profileFor(role)
        );
    }

    private static AdminUserUpdateCommand updateCommand(Role role) {
        return new AdminUserUpdateCommand(
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            role,
            profileFor(role)
        );
    }

    private static UserProfile profileFor(Role role) {
        return switch (role) {
            case STUDENT -> new StudentProfile("R2026001", 3, "2026");
            case ORGANIZER -> new OrganizerProfile("Club Coordinator");
            case FACULTY -> new FacultyProfile("E1001", "Professor");
            case ADMIN -> new AdminProfile(2);
        };
    }

    private static AdminUserCreateCommand createCommand() {
        return new AdminUserCreateCommand(
            "Ayesha Khan",
            "ayesha@example.edu",
            "initial-password",
            "Computing",
            Role.STUDENT,
            new StudentProfile("R2026001", 3, "2026")
        );
    }

    private static AdminUserRow studentRow() {
        return new AdminUserRow(
            7L,
            "Ayesha Khan",
            "ayesha@example.com",
            "Computing",
            Role.STUDENT,
            true,
            new StudentProfile("R2026001", 3, "2026")
        );
    }

    private static UserAccount account() {
        return new UserAccount(
            7L,
            "Alice Khan",
            "alice@example.com",
            "$2a$10$0123456789012345678901234567890123456789012345678901",
            "Computing",
            Role.STUDENT,
            true
        );
    }
}
