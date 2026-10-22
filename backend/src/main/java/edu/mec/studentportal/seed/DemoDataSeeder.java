package edu.mec.studentportal.seed;

import edu.mec.studentportal.user.AdminProfile;
import edu.mec.studentportal.user.AdminUserCreateCommand;
import edu.mec.studentportal.user.FacultyProfile;
import edu.mec.studentportal.user.OrganizerProfile;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.StudentProfile;
import edu.mec.studentportal.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in demo-account seeder for local development and demos. Double opt-in: the application must
 * run with {@code demo.seed=true} (environment variable {@code DEMO_SEED=true}) AND the datasource
 * URL must target the disposable {@code student_portal} schema, otherwise the run fails fast or
 * skips. Accounts are provisioned through {@link UserService#createUser}, so passwords are
 * BCrypt-hashed and each role profile row is created transactionally — never plain-text SQL.
 *
 * <p>Seeding is idempotent by content: if any user already exists, it logs and does nothing, so a
 * partially used demo database is never duplicated.
 */
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true", matchIfMissing = false)
public class DemoDataSeeder implements CommandLineRunner {
    static final String DEMO_DOMAIN = "@demo.mec.edu";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;
    private final String datasourceUrl;

    public DemoDataSeeder(
        UserService userService,
        JdbcTemplate jdbcTemplate,
        @Value("${spring.datasource.url}") String datasourceUrl
    ) {
        this.userService = userService;
        this.jdbcTemplate = jdbcTemplate;
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    @Transactional
    public void run(String... args) {
        DatabaseTargetValidator.validate(datasourceUrl);

        Integer existingUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users",
            Integer.class
        );
        if (existingUsers != null && existingUsers > 0) {
            log.info(
                "Demo seed skipped: {} user(s) already exist. Delete local data only if you own "
                    + "this disposable database.",
                existingUsers
            );
            return;
        }

        long adminId = provision(
            "Demo Admin",
            "admin" + DEMO_DOMAIN,
            "DemoAdmin123",
            "Administration",
            Role.ADMIN,
            new AdminProfile()
        );
        long facultyId = provision(
            "Demo Faculty",
            "faculty" + DEMO_DOMAIN,
            "DemoFaculty123",
            "CSE",
            Role.FACULTY,
            new FacultyProfile("FAC-1001", "Professor")
        );
        long organizerId = provision(
            "Demo Organizer",
            "organizer" + DEMO_DOMAIN,
            "DemoOrganizer123",
            "CSE",
            Role.ORGANIZER,
            new OrganizerProfile("Event Convenor")
        );
        long studentId = provision(
            "Demo Student",
            "student" + DEMO_DOMAIN,
            "DemoStudent123",
            "CSE",
            Role.STUDENT,
            new StudentProfile("DEMO2026001", 3, "2026")
        );

        log.info(
            "Demo seed complete — admin id={} (admin{} / DemoAdmin123), faculty id={} "
                + "(faculty{} / DemoFaculty123), organizer id={} (organizer{} / DemoOrganizer123), "
                + "student id={} (student{} / DemoStudent123). Local demo credentials only.",
            adminId, DEMO_DOMAIN,
            facultyId, DEMO_DOMAIN,
            organizerId, DEMO_DOMAIN,
            studentId, DEMO_DOMAIN
        );
    }

    private long provision(
        String fullName,
        String email,
        String password,
        String department,
        Role role,
        edu.mec.studentportal.user.UserProfile profile
    ) {
        return userService.createUser(
            new AdminUserCreateCommand(
                fullName,
                email,
                password,
                department,
                role,
                profile
            )
        ).id();
    }
}
