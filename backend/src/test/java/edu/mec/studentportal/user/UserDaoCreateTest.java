package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

class UserDaoCreateTest {
    @Test
    void insertsUserAndReturnsGeneratedId() throws Exception {
        InsertRecordingJdbcTemplate jdbcTemplate =
            new InsertRecordingJdbcTemplate();
        UserDao userDao = new UserDao(jdbcTemplate);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(
            anyString(),
            eq(Statement.RETURN_GENERATED_KEYS)
        )).thenReturn(statement);

        long id = userDao.insertUser(
            "Ayesha Khan",
            "ayesha@example.edu",
            "$2a$10$hashed",
            "Computing",
            Role.STUDENT
        );

        assertThat(id).isEqualTo(51L);
        assertThat(jdbcTemplate.createdId).isEqualTo(51L);

        jdbcTemplate.creator.createPreparedStatement(connection);
        verify(connection).prepareStatement(
            org.mockito.ArgumentMatchers.contains(
                "INSERT INTO users"
            ),
            eq(Statement.RETURN_GENERATED_KEYS)
        );
        verify(statement).setString(1, "Ayesha Khan");
        verify(statement).setString(2, "ayesha@example.edu");
        verify(statement).setString(3, "$2a$10$hashed");
        verify(statement).setString(4, "Computing");
        verify(statement).setString(5, "STUDENT");
    }

    @Test
    void rejectsNullInsertValues() {
        UserDao userDao = new UserDao(new RecordingJdbcTemplate());

        assertThatThrownBy(() -> userDao.insertUser(
            null,
            "ayesha@example.edu",
            "$2a$10$hashed",
            "Computing",
            Role.STUDENT
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("user insert values must not be null");
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void insertsMatchingProfileRow(Role role) {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        UserDao userDao = new UserDao(jdbcTemplate);

        userDao.insertProfile(51L, profileFor(role));

        assertThat(jdbcTemplate.sql).contains("INSERT INTO " + tableFor(role));
        assertThat(jdbcTemplate.arguments).containsExactly(
            expectedArgumentsFor(role)
        );
    }

    @Test
    void rejectsNullProfile() {
        UserDao userDao = new UserDao(new RecordingJdbcTemplate());

        assertThatThrownBy(() -> userDao.insertProfile(51L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("profile must not be null");
    }

    private static UserProfile profileFor(Role role) {
        return switch (role) {
            case STUDENT -> new StudentProfile("R2026001", 3, "2026");
            case ORGANIZER -> new OrganizerProfile("Club Coordinator");
            case FACULTY -> new FacultyProfile("E1001", "Professor");
            case ADMIN -> new AdminProfile(2);
        };
    }

    private static String tableFor(Role role) {
        return switch (role) {
            case STUDENT -> "student_profiles";
            case ORGANIZER -> "organizer_profiles";
            case FACULTY -> "faculty_profiles";
            case ADMIN -> "admin_profiles";
        };
    }

    private static Object[] expectedArgumentsFor(Role role) {
        return switch (role) {
            case STUDENT -> new Object[] {51L, "R2026001", 3, "2026"};
            case ORGANIZER -> new Object[] {51L, "Club Coordinator"};
            case FACULTY -> new Object[] {51L, "E1001", "Professor"};
            case ADMIN -> new Object[] {51L, 2};
        };
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] arguments;

        private RecordingJdbcTemplate() {
            super(mock(DataSource.class));
        }

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.arguments = args;
            return 1;
        }
    }

    private static final class InsertRecordingJdbcTemplate
        extends JdbcTemplate {
        private PreparedStatementCreator creator;
        private long createdId;

        private InsertRecordingJdbcTemplate() {
            super(mock(DataSource.class));
        }

        @Override
        public int update(
            PreparedStatementCreator preparedStatementCreator,
            KeyHolder keyHolder
        ) {
            this.creator = preparedStatementCreator;
            GeneratedKeyHolder generatedKeyHolder =
                (GeneratedKeyHolder) keyHolder;
            generatedKeyHolder.getKeyList().add(
                Map.of("GENERATED_KEY", 51L)
            );
            this.createdId = 51L;
            return 1;
        }
    }
}
