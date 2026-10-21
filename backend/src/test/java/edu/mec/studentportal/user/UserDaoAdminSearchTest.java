package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class UserDaoAdminSearchTest {
    @Test
    void appliesRoleEnabledAndSearchFiltersWithPagination() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.countResult = 25L;
        UserDao userDao = new UserDao(jdbcTemplate);

        AdminUserSearchResult result = userDao.searchAdminUsers(
            new AdminUserListQuery(2, 10, Role.STUDENT, true, "Ayesha")
        );

        assertThat(result.totalItems()).isEqualTo(25);
        assertThat(result.users()).isEmpty();
        assertThat(jdbcTemplate.countSql)
            .contains("u.role = ?")
            .contains("u.enabled = ?")
            .contains("LOCATE(LOWER(?), LOWER(u.full_name))")
            .contains("LOCATE(LOWER(?), LOWER(u.email))");
        assertThat(jdbcTemplate.countArguments)
            .containsExactly("STUDENT", true, "Ayesha", "Ayesha");
        assertThat(jdbcTemplate.listSql)
            .contains("ORDER BY u.id ASC LIMIT ? OFFSET ?");
        assertThat(jdbcTemplate.listArguments)
            .containsExactly("STUDENT", true, "Ayesha", "Ayesha", 10, 20L);
    }

    @Test
    void usesDefaultsWithoutFilters() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        UserDao userDao = new UserDao(jdbcTemplate);

        AdminUserSearchResult result = userDao.searchAdminUsers(
            new AdminUserListQuery(0, 20, null, null, null)
        );

        assertThat(result.totalItems()).isZero();
        assertThat(jdbcTemplate.countArguments).isEmpty();
        assertThat(jdbcTemplate.listArguments).containsExactly(20, 0L);
        assertThat(jdbcTemplate.countSql)
            .doesNotContain("u.role = ?")
            .doesNotContain("u.enabled = ?")
            .doesNotContain("LOCATE");
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void mapsEachRoleToItsMatchingProfile(Role role) throws SQLException {
        ResultSet resultSet = baseRow(role);

        AdminUserRow row = UserDao.mapAdminUserRow(resultSet);

        assertThat(row.id()).isEqualTo(7L);
        assertThat(row.role()).isEqualTo(role);
        assertThat(row.profile())
            .usingRecursiveComparison()
            .isEqualTo(expectedProfile(role));
    }

    @Test
    void rejectsMissingMatchingProfile() throws SQLException {
        ResultSet resultSet = baseRow(Role.STUDENT);
        when(resultSet.getBoolean("has_student_profile")).thenReturn(false);

        assertThatThrownBy(() -> UserDao.mapAdminUserRow(resultSet))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Role/profile mismatch for user 7");
    }

    @Test
    void rejectsAnExtraProfileForTheRole() throws SQLException {
        ResultSet resultSet = baseRow(Role.STUDENT);
        when(resultSet.getBoolean("has_admin_profile")).thenReturn(true);

        assertThatThrownBy(() -> UserDao.mapAdminUserRow(resultSet))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Role/profile mismatch for user 7");
    }

    private static ResultSet baseRow(Role role) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(7L);
        when(resultSet.getString("full_name")).thenReturn("Ayesha Khan");
        when(resultSet.getString("email")).thenReturn("ayesha@example.edu");
        when(resultSet.getString("department")).thenReturn("Computing");
        when(resultSet.getString("role")).thenReturn(role.name());
        when(resultSet.getBoolean("enabled")).thenReturn(true);
        when(resultSet.getBoolean("has_student_profile"))
            .thenReturn(role == Role.STUDENT);
        when(resultSet.getBoolean("has_organizer_profile"))
            .thenReturn(role == Role.ORGANIZER);
        when(resultSet.getBoolean("has_faculty_profile"))
            .thenReturn(role == Role.FACULTY);
        when(resultSet.getBoolean("has_admin_profile"))
            .thenReturn(role == Role.ADMIN);
        when(resultSet.getString("roll_number")).thenReturn("R2026001");
        when(resultSet.getInt("semester")).thenReturn(3);
        when(resultSet.getString("batch")).thenReturn("2026");
        when(resultSet.getString("organizer_designation"))
            .thenReturn("Club Coordinator");
        when(resultSet.getString("employee_id")).thenReturn("E1001");
        when(resultSet.getString("faculty_designation"))
            .thenReturn("Professor");
        when(resultSet.getInt("admin_level")).thenReturn(2);
        return resultSet;
    }

    private static UserProfile expectedProfile(Role role) {
        return switch (role) {
            case STUDENT -> new StudentProfile("R2026001", 3, "2026");
            case ORGANIZER -> new OrganizerProfile("Club Coordinator");
            case FACULTY -> new FacultyProfile("E1001", "Professor");
            case ADMIN -> new AdminProfile(2);
        };
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String countSql;
        private Object[] countArguments;
        private String listSql;
        private Object[] listArguments;
        private Long countResult = 0L;
        private List<AdminUserRow> queryResult = List.of();

        private RecordingJdbcTemplate() {
            super(mock(DataSource.class));
        }

        @Override
        public <T> T queryForObject(
            String sql,
            Class<T> requiredType,
            Object... args
        ) {
            countSql = sql;
            countArguments = args;
            return requiredType.cast(countResult);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(
            String sql,
            RowMapper<T> rowMapper,
            Object... args
        ) {
            listSql = sql;
            listArguments = args;
            return (List<T>) queryResult;
        }
    }
}
