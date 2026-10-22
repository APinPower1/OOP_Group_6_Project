package edu.mec.studentportal.seed;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

class AdminBootstrapRunnerTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final Environment environment = mock(Environment.class);

    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        runner = new AdminBootstrapRunner(
            dataSource,
            jdbcTemplate,
            transactionTemplate,
            passwordEncoder,
            environment
        );
    }

    @Test
    void doesNothingUnlessExplicitBootstrapOptionIsPassed() throws Exception {
        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(dataSource, jdbcTemplate, transactionTemplate, passwordEncoder);
        verify(environment, never()).getProperty("spring.datasource.url");
    }

    @Test
    void refusesDatabaseUrlsOutsideStudentPortalBeforeConnecting() {
        when(environment.getProperty("spring.datasource.url"))
            .thenReturn("jdbc:mysql://localhost:3306/student_portal_test");

        assertThrows(
            IllegalArgumentException.class,
            () -> runner.run(bootstrapArguments())
        );

        verifyNoInteractions(dataSource, jdbcTemplate, transactionTemplate, passwordEncoder);
    }

    @Test
    void refusesRemoteDatabaseHostsBeforeConnecting() {
        when(environment.getProperty("spring.datasource.url"))
            .thenReturn("jdbc:mysql://db.example.internal:3306/student_portal");

        assertThrows(
            IllegalArgumentException.class,
            () -> runner.run(bootstrapArguments())
        );

        verifyNoInteractions(dataSource, jdbcTemplate, transactionTemplate, passwordEncoder);
    }

    @Test
    void refusesPasswordsShorterThanEightBytesBeforeConnecting() {
        configureValidDatabaseUrl();
        when(environment.getProperty("ADMIN_BOOTSTRAP_NAME", "Portal Administrator"))
            .thenReturn("Portal Administrator");
        when(environment.getProperty("ADMIN_BOOTSTRAP_EMAIL", (String) null))
            .thenReturn("admin@example.edu");
        when(environment.getProperty("ADMIN_BOOTSTRAP_DEPARTMENT", "Administration"))
            .thenReturn("Administration");
        when(environment.getProperty("ADMIN_BOOTSTRAP_PASSWORD", (String) null)).thenReturn("admin");

        assertThrows(
            IllegalArgumentException.class,
            () -> runner.run(bootstrapArguments())
        );

        verifyNoInteractions(dataSource, jdbcTemplate, transactionTemplate, passwordEncoder);
    }

    private void configureValidDatabaseUrl() {
        when(environment.getProperty("spring.datasource.url"))
            .thenReturn("jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata");
    }

    private static DefaultApplicationArguments bootstrapArguments() {
        return new DefaultApplicationArguments("--bootstrap-admin");
    }
}
