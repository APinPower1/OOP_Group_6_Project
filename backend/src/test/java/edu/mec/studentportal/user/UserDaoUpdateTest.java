package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class UserDaoUpdateTest {
    @Test
    void findsAdminUserForUpdate() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        AdminUserRow row = new AdminUserRow(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.STUDENT,
            true,
            "$2a$10$hash",
            new StudentProfile("R2026001", 3, "2026")
        );
        jdbcTemplate.queryResult = List.of(row);
        UserDao userDao = new UserDao(jdbcTemplate);

        Optional<AdminUserRow> result =
            userDao.findAdminUserByIdForUpdate(41L);

        assertThat(result).contains(row);
        assertThat(jdbcTemplate.sql)
            .contains("WHERE u.id = ? FOR UPDATE");
        assertThat(jdbcTemplate.arguments).containsExactly(41L);
    }

    @Test
    void locksEnabledAdministratorsInIdOrder() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryResult = List.of(1L, 2L);
        UserDao userDao = new UserDao(jdbcTemplate);

        assertThat(userDao.lockEnabledAdministratorIds())
            .containsExactly(1L, 2L);
        assertThat(jdbcTemplate.sql)
            .contains("role = 'ADMIN'")
            .contains("enabled = TRUE")
            .contains("ORDER BY id")
            .contains("FOR UPDATE");
    }

    @Test
    void updatesAccountFieldsWithoutPasswordOrEnabled() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        UserDao userDao = new UserDao(jdbcTemplate);

        userDao.updateUser(
            41L,
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            Role.FACULTY
        );

        assertThat(jdbcTemplate.sql)
            .contains("UPDATE users")
            .contains("SET full_name = ?")
            .contains("email = ?")
            .contains("department = ?")
            .contains("role = ?")
            .doesNotContain("password_hash")
            .doesNotContain("enabled");
        assertThat(jdbcTemplate.arguments).containsExactly(
            "Ayesha Khan",
            "ayesha@example.edu",
            "Computing",
            "FACULTY",
            41L
        );
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void updatesMatchingProfileRowInPlace(Role role) {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        UserDao userDao = new UserDao(jdbcTemplate);

        userDao.updateProfile(41L, profileFor(role));

        assertThat(jdbcTemplate.sql)
            .contains("UPDATE " + tableFor(role))
            .contains("WHERE user_id = ?");
        assertThat(jdbcTemplate.arguments).containsExactly(
            expectedProfileArgumentsFor(role)
        );
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void deletesOldRoleProfile(Role role) {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        UserDao userDao = new UserDao(jdbcTemplate);

        userDao.deleteProfile(41L, role);

        assertThat(jdbcTemplate.sql)
            .contains("DELETE FROM " + tableFor(role))
            .contains("WHERE user_id = ?");
        assertThat(jdbcTemplate.arguments).containsExactly(41L);
    }

    @Test
    void detectsStudentHistoryAcrossAllHistoryTables() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryForObjectResult = 1;
        UserDao userDao = new UserDao(jdbcTemplate);

        assertThat(userDao.hasStudentHistory(41L)).isTrue();
        assertThat(jdbcTemplate.sql)
            .contains("registrations")
            .contains("club_members")
            .contains("feedback")
            .doesNotContain("status");
        assertThat(jdbcTemplate.arguments)
            .containsExactly(41L, 41L, 41L);
    }

    @Test
    void reportsNoStudentHistoryWhenAllTablesAreEmpty() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryForObjectResult = 0;
        UserDao userDao = new UserDao(jdbcTemplate);

        assertThat(userDao.hasStudentHistory(41L)).isFalse();
    }

    @Test
    void failsWhenProfileUpdateAffectsNoRow() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateRows = 0;
        UserDao userDao = new UserDao(jdbcTemplate);

        assertThatThrownBy(() -> userDao.updateProfile(
            41L,
            new OrganizerProfile("Club Coordinator")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Profile update did not affect a row for user 41"
            );
    }

    @Test
    void failsWhenProfileDeleteAffectsNoRow() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateRows = 0;
        UserDao userDao = new UserDao(jdbcTemplate);

        assertThatThrownBy(() -> userDao.deleteProfile(
            41L,
            Role.ORGANIZER
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Profile delete did not affect a row for user 41"
            );
    }

    @Test
    void disablesOnlyTheEnabledFlag() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        UserDao userDao = new UserDao(jdbcTemplate);

        assertThat(userDao.disableUser(41L)).isEqualTo(1);

        assertThat(jdbcTemplate.sql)
            .contains("UPDATE users")
            .contains("SET enabled = FALSE")
            .contains("WHERE id = ?")
            .doesNotContain("password_hash")
            .doesNotContain("full_name")
            .doesNotContain("email")
            .doesNotContain("department")
            .doesNotContain("role")
            .doesNotContain("DELETE");
        assertThat(jdbcTemplate.arguments).containsExactly(41L);
    }

    @Test
    void reportsDisableRowCount() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateRows = 0;
        UserDao userDao = new UserDao(jdbcTemplate);

        assertThat(userDao.disableUser(41L)).isZero();
    }

    @Test
    void enablesOnlyTheEnabledFlag() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        UserDao userDao = new UserDao(jdbcTemplate);

        assertThat(userDao.enableUser(41L)).isEqualTo(1);

        assertThat(jdbcTemplate.sql)
            .contains("UPDATE users")
            .contains("SET enabled = TRUE")
            .contains("WHERE id = ?")
            .doesNotContain("password_hash")
            .doesNotContain("full_name")
            .doesNotContain("email")
            .doesNotContain("department")
            .doesNotContain("role")
            .doesNotContain("DELETE");
        assertThat(jdbcTemplate.arguments).containsExactly(41L);
    }

    @Test
    void reportsEnableRowCount() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateRows = 0;
        UserDao userDao = new UserDao(jdbcTemplate);

        assertThat(userDao.enableUser(41L)).isZero();
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

    private static Object[] expectedProfileArgumentsFor(Role role) {
        return switch (role) {
            case STUDENT -> new Object[] {"R2026001", 3, "2026", 41L};
            case ORGANIZER -> new Object[] {"Club Coordinator", 41L};
            case FACULTY -> new Object[] {"E1001", "Professor", 41L};
            case ADMIN -> new Object[] {2, 41L};
        };
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] arguments;
        private List<?> queryResult = List.of();
        private int updateRows = 1;
        private Integer queryForObjectResult = 0;

        private RecordingJdbcTemplate() {
            super(mock(DataSource.class));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            this.sql = sql;
            this.arguments = new Object[0];
            return (List<T>) queryResult;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(
            String sql,
            RowMapper<T> rowMapper,
            Object... args
        ) {
            this.sql = sql;
            this.arguments = args;
            return (List<T>) queryResult;
        }

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.arguments = args;
            return updateRows;
        }

        @Override
        public <T> T queryForObject(
            String sql,
            Class<T> requiredType,
            Object... args
        ) {
            this.sql = sql;
            this.arguments = args;
            return requiredType.cast(queryForObjectResult);
        }
    }
}
