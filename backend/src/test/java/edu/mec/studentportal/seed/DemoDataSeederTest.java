package edu.mec.studentportal.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.mec.studentportal.user.AdminUserCreateCommand;
import edu.mec.studentportal.user.AdminUserResponse;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.UserService;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {
    private static final String SAFE_URL =
        "jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata";

    @Mock
    private UserService userService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AtomicLong nextUserId;

    @BeforeEach
    void setUp() {
        nextUserId = new AtomicLong(100L);
    }

    @Test
    void skipsWhenAnyUserAlreadyExists() {
        DemoDataSeeder seeder = new DemoDataSeeder(userService, jdbcTemplate, SAFE_URL);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
            .thenReturn(7);

        seeder.run();

        verify(userService, never()).createUser(any());
    }

    @Test
    void refusesWrongSchemaBeforeTouchingUsers() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            userService,
            jdbcTemplate,
            "jdbc:mysql://localhost:3306/student_portal_test?serverTimezone=Asia/Kolkata"
        );

        assertThatThrownBy(seeder::run)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("student_portal");
        verify(userService, never()).createUser(any());
        verify(jdbcTemplate, never()).queryForObject(any(String.class), any(Class.class));
    }

    @Test
    void provisionsOneAccountPerRoleWithProfiles() {
        DemoDataSeeder seeder = new DemoDataSeeder(userService, jdbcTemplate, SAFE_URL);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
            .thenReturn(0);
        when(userService.createUser(any())).thenAnswer(invocation -> {
            AdminUserCreateCommand command = invocation.getArgument(0);
            return new AdminUserResponse(
                nextUserId.incrementAndGet(),
                command.fullName(),
                command.email(),
                command.department(),
                command.role(),
                true,
                command.profile()
            );
        });

        seeder.run();

        ArgumentCaptor<AdminUserCreateCommand> captor =
            ArgumentCaptor.forClass(AdminUserCreateCommand.class);
        verify(userService, times(4)).createUser(captor.capture());

        List<AdminUserCreateCommand> commands = captor.getAllValues();
        assertThat(commands)
            .extracting(AdminUserCreateCommand::role)
            .containsExactly(
                Role.ADMIN,
                Role.FACULTY,
                Role.ORGANIZER,
                Role.STUDENT
            );
        assertThat(commands)
            .extracting(AdminUserCreateCommand::email)
            .containsExactly(
                "admin@demo.mec.edu",
                "faculty@demo.mec.edu",
                "organizer@demo.mec.edu",
                "student@demo.mec.edu"
            );
        assertThat(commands.get(0).profile()).isInstanceOf(
            edu.mec.studentportal.user.AdminProfile.class
        );
        assertThat(commands.get(1).profile()).isInstanceOf(
            edu.mec.studentportal.user.FacultyProfile.class
        );
        assertThat(commands.get(2).profile()).isInstanceOf(
            edu.mec.studentportal.user.OrganizerProfile.class
        );
        assertThat(commands.get(3).profile()).isInstanceOf(
            edu.mec.studentportal.user.StudentProfile.class
        );
        assertThat(commands).allSatisfy(command ->
            assertThat(command.password()).hasSizeGreaterThan(7)
        );
    }

    @Test
    void treatsMissingUserCountAsEmptyDatabase() {
        DemoDataSeeder seeder = new DemoDataSeeder(userService, jdbcTemplate, SAFE_URL);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
            .thenReturn(null);
        when(userService.createUser(any())).thenAnswer(invocation -> {
            AdminUserCreateCommand command = invocation.getArgument(0);
            return new AdminUserResponse(
                nextUserId.incrementAndGet(),
                command.fullName(),
                command.email(),
                command.department(),
                command.role(),
                true,
                command.profile()
            );
        });

        seeder.run();

        verify(userService, times(4)).createUser(any());
    }
}
