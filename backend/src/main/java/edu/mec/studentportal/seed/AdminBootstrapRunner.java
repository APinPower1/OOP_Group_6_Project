package edu.mec.studentportal.seed;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final String LOCK_NAME = "student_portal_first_admin_bootstrap";
    private static final String BOOTSTRAP_OPTION = "bootstrap-admin";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminBootstrapRunner(
        DataSource dataSource,
        JdbcTemplate jdbcTemplate,
        TransactionTemplate transactionTemplate,
        PasswordEncoder passwordEncoder,
        Environment environment
    ) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        if (!arguments.containsOption(BOOTSTRAP_OPTION)) {
            return;
        }

        DatabaseTargetValidator.validateLocal(environment.getProperty("spring.datasource.url"));
        BootstrapAccount account = BootstrapAccount.from(environment);

        try (Connection lockConnection = dataSource.getConnection()) {
            acquireLock(lockConnection);
            try {
                transactionTemplate.executeWithoutResult(status -> createFirstAdmin(account));
                LOGGER.info("First administrator account created.");
            } finally {
                releaseLock(lockConnection);
            }
        }
    }

    private static void acquireLock(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 10)")) {
            statement.setString(1, LOCK_NAME);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException(
                        "Could not acquire the first-admin bootstrap lock; no account was created."
                    );
                }
            }
        }
    }

    private static void releaseLock(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, LOCK_NAME);
            statement.executeQuery().close();
        }
    }

    private void createFirstAdmin(BootstrapAccount account) {
        Long existingAdmin = jdbcTemplate.query(
            "SELECT id FROM users WHERE role = 'ADMIN' LIMIT 1",
            resultSet -> resultSet.next() ? resultSet.getLong(1) : null
        );
        if (existingAdmin != null) {
            throw new IllegalStateException(
                "An administrator account already exists; first-admin bootstrap was refused."
            );
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    INSERT INTO users (full_name, email, password_hash, department, role, enabled)
                    VALUES (?, ?, ?, ?, 'ADMIN', TRUE)
                    """,
                new String[] {"id"}
            );
            statement.setString(1, account.fullName());
            statement.setString(2, account.email());
            statement.setString(3, passwordEncoder.encode(account.password()));
            statement.setString(4, account.department());
            return statement;
        }, keyHolder);

        Number userId = keyHolder.getKey();
        if (userId == null) {
            throw new IllegalStateException("The administrator account was not created.");
        }
        jdbcTemplate.update(
            "INSERT INTO admin_profiles (user_id, admin_level) VALUES (?, 1)",
            userId.longValue()
        );
    }

    private record BootstrapAccount(
        String fullName,
        String email,
        String department,
        String password
    ) {
        private static BootstrapAccount from(Environment environment) {
            String fullName = required(environment, "ADMIN_BOOTSTRAP_NAME", "Portal Administrator");
            String email = required(environment, "ADMIN_BOOTSTRAP_EMAIL", null)
                .toLowerCase(Locale.ROOT);
            String department = required(environment, "ADMIN_BOOTSTRAP_DEPARTMENT", "Administration");
            String password = required(environment, "ADMIN_BOOTSTRAP_PASSWORD", null);

            if (fullName.length() > 120) {
                throw new IllegalArgumentException("ADMIN_BOOTSTRAP_NAME must be at most 120 characters.");
            }
            if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("ADMIN_BOOTSTRAP_EMAIL must be a valid email address.");
            }
            if (department.length() > 120) {
                throw new IllegalArgumentException(
                    "ADMIN_BOOTSTRAP_DEPARTMENT must be at most 120 characters."
                );
            }
            int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
            if (password.isBlank() || passwordBytes < 8 || passwordBytes > 72) {
                throw new IllegalArgumentException(
                    "ADMIN_BOOTSTRAP_PASSWORD must be between 8 and 72 UTF-8 bytes."
                );
            }
            return new BootstrapAccount(fullName, email, department, password);
        }

        private static String required(Environment environment, String key, String defaultValue) {
            String value = environment.getProperty(key, defaultValue);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(key + " must be configured for first-admin bootstrap.");
            }
            return value.trim();
        }
    }
}
