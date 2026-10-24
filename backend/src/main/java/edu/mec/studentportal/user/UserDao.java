package edu.mec.studentportal.user;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserDao {
    private static final String SELECT_USER_COLUMNS = """
        SELECT id, full_name, email, password_hash, department, role, enabled,
               must_change_password
        FROM users
        """;

    private static final String SELECT_ADMIN_USER_COLUMNS = """
        SELECT u.id, u.full_name, u.email, u.department, u.role, u.enabled,
               u.password_hash, u.must_change_password,
               sp.user_id IS NOT NULL AS has_student_profile,
               sp.roll_number, sp.semester, sp.batch,
               op.user_id IS NOT NULL AS has_organizer_profile,
               op.designation AS organizer_designation,
               fp.user_id IS NOT NULL AS has_faculty_profile,
               fp.employee_id, fp.designation AS faculty_designation,
               ap.user_id IS NOT NULL AS has_admin_profile,
               ap.admin_level
        FROM users u
        LEFT JOIN student_profiles sp ON sp.user_id = u.id
        LEFT JOIN organizer_profiles op ON op.user_id = u.id
        LEFT JOIN faculty_profiles fp ON fp.user_id = u.id
        LEFT JOIN admin_profiles ap ON ap.user_id = u.id
        """;

    private static final String LOCK_ENABLED_ADMINISTRATORS = """
        SELECT id FROM users
        WHERE role = 'ADMIN' AND enabled = TRUE
        ORDER BY id
        FOR UPDATE
        """;
    private static final String UPDATE_USER = """
        UPDATE users
        SET full_name = ?, email = ?, department = ?, role = ?
        WHERE id = ?
        """;
    private static final String DISABLE_USER = """
        UPDATE users
        SET enabled = FALSE
        WHERE id = ?
        """;
    private static final String ENABLE_USER = """
        UPDATE users
        SET enabled = TRUE
        WHERE id = ?
        """;
    private static final String UPDATE_PASSWORD = """
        UPDATE users
        SET password_hash = ?, must_change_password = ?
        WHERE id = ?
        """;
    private static final String UPDATE_STUDENT_PROFILE = """
        UPDATE student_profiles
        SET roll_number = ?, semester = ?, batch = ?
        WHERE user_id = ?
        """;
    private static final String UPDATE_ORGANIZER_PROFILE = """
        UPDATE organizer_profiles
        SET designation = ?
        WHERE user_id = ?
        """;
    private static final String UPDATE_FACULTY_PROFILE = """
        UPDATE faculty_profiles
        SET employee_id = ?, designation = ?
        WHERE user_id = ?
        """;
    private static final String UPDATE_ADMIN_PROFILE = """
        UPDATE admin_profiles
        SET admin_level = ?
        WHERE user_id = ?
        """;
    private static final String DELETE_STUDENT_PROFILE =
        "DELETE FROM student_profiles WHERE user_id = ?";
    private static final String DELETE_ORGANIZER_PROFILE =
        "DELETE FROM organizer_profiles WHERE user_id = ?";
    private static final String DELETE_FACULTY_PROFILE =
        "DELETE FROM faculty_profiles WHERE user_id = ?";
    private static final String DELETE_ADMIN_PROFILE =
        "DELETE FROM admin_profiles WHERE user_id = ?";
    private static final String STUDENT_HISTORY_EXISTS = """
        SELECT (EXISTS(SELECT 1 FROM registrations WHERE student_id = ?)
            + EXISTS(SELECT 1 FROM club_members WHERE student_id = ?)
            + EXISTS(SELECT 1 FROM feedback WHERE student_id = ?))
        """;

    private static final String INSERT_USER = """
        INSERT INTO users (full_name, email, password_hash, department, role, enabled)
        VALUES (?, ?, ?, ?, ?, TRUE)
        """;
    private static final String INSERT_STUDENT_PROFILE = """
        INSERT INTO student_profiles (user_id, roll_number, semester, batch)
        VALUES (?, ?, ?, ?)
        """;
    private static final String INSERT_ORGANIZER_PROFILE = """
        INSERT INTO organizer_profiles (user_id, designation)
        VALUES (?, ?)
        """;
    private static final String INSERT_FACULTY_PROFILE = """
        INSERT INTO faculty_profiles (user_id, employee_id, designation)
        VALUES (?, ?, ?)
        """;
    private static final String INSERT_ADMIN_PROFILE = """
        INSERT INTO admin_profiles (user_id, admin_level)
        VALUES (?, ?)
        """;

    private static final RowMapper<UserAccount> USER_ACCOUNT_MAPPER =
        (resultSet, rowNumber) -> mapAccount(resultSet);
    private static final RowMapper<UserSecurityState> USER_SECURITY_STATE_MAPPER =
        (resultSet, rowNumber) -> mapSecurityState(resultSet);
    private static final RowMapper<AdminUserRow> ADMIN_USER_ROW_MAPPER =
        (resultSet, rowNumber) -> mapAdminUserRow(resultSet);

    private final JdbcTemplate jdbcTemplate;

    public UserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        List<UserAccount> users = jdbcTemplate.query(
            SELECT_USER_COLUMNS + " WHERE email = ?",
            USER_ACCOUNT_MAPPER,
            email
        );
        return users.stream().findFirst();
    }

    public Optional<UserAccount> findById(long userId) {
        List<UserAccount> users = jdbcTemplate.query(
            SELECT_USER_COLUMNS + " WHERE id = ?",
            USER_ACCOUNT_MAPPER,
            userId
        );
        return users.stream().findFirst();
    }

    public Optional<UserSecurityState> findSecurityStateById(long userId) {
        List<UserSecurityState> states = jdbcTemplate.query(
            "SELECT id, role, enabled FROM users WHERE id = ?",
            USER_SECURITY_STATE_MAPPER,
            userId
        );
        return states.stream().findFirst();
    }

    public long insertUser(
        String fullName,
        String email,
        String passwordHash,
        String department,
        Role role
    ) {
        if (fullName == null || email == null || passwordHash == null
            || department == null || role == null) {
            throw new IllegalArgumentException(
                "user insert values must not be null"
            );
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int rows = jdbcTemplate.update(
            connection -> {
                PreparedStatement statement = connection.prepareStatement(
                    INSERT_USER,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, fullName);
                statement.setString(2, email);
                statement.setString(3, passwordHash);
                statement.setString(4, department);
                statement.setString(5, role.name());
                return statement;
            },
            keyHolder
        );
        Number generatedId = keyHolder.getKey();
        if (rows != 1 || generatedId == null) {
            throw new IllegalStateException(
                "User insert did not return a generated id"
            );
        }
        return generatedId.longValue();
    }

    public void insertProfile(long userId, UserProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        switch (profile) {
            case StudentProfile student -> jdbcTemplate.update(
                INSERT_STUDENT_PROFILE,
                userId,
                student.getRollNumber(),
                student.getSemester(),
                student.getBatch()
            );
            case OrganizerProfile organizer -> jdbcTemplate.update(
                INSERT_ORGANIZER_PROFILE,
                userId,
                organizer.getDesignation()
            );
            case FacultyProfile faculty -> jdbcTemplate.update(
                INSERT_FACULTY_PROFILE,
                userId,
                faculty.getEmployeeId(),
                faculty.getDesignation()
            );
            case AdminProfile admin -> jdbcTemplate.update(
                INSERT_ADMIN_PROFILE,
                userId,
                admin.getAdminLevel()
            );
        }
    }

    public AdminUserSearchResult searchAdminUsers(AdminUserListQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        SearchSql search = buildAdminUserSearch(query);
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users u" + search.where(),
            Long.class,
            search.arguments().toArray()
        );
        long totalItems = count == null ? 0L : count;

        List<Object> pageArguments = new ArrayList<>(search.arguments());
        pageArguments.add(query.size());
        pageArguments.add((long) query.page() * query.size());

        List<AdminUserRow> users = jdbcTemplate.query(
            SELECT_ADMIN_USER_COLUMNS
                + search.where()
                + " ORDER BY u.id ASC LIMIT ? OFFSET ?",
            ADMIN_USER_ROW_MAPPER,
            pageArguments.toArray()
        );
        return new AdminUserSearchResult(users, totalItems);
    }

    public Optional<AdminUserRow> findAdminUserByIdForUpdate(long userId) {
        List<AdminUserRow> users = jdbcTemplate.query(
            SELECT_ADMIN_USER_COLUMNS + " WHERE u.id = ? FOR UPDATE",
            ADMIN_USER_ROW_MAPPER,
            userId
        );
        return users.stream().findFirst();
    }

    public List<Long> lockEnabledAdministratorIds() {
        return jdbcTemplate.query(
            LOCK_ENABLED_ADMINISTRATORS,
            (resultSet, rowNumber) -> resultSet.getLong("id")
        );
    }

    public int updateUser(
        long userId,
        String fullName,
        String email,
        String department,
        Role role
    ) {
        if (fullName == null || email == null || department == null
            || role == null) {
            throw new IllegalArgumentException(
                "user update values must not be null"
            );
        }
        return jdbcTemplate.update(
            UPDATE_USER,
            fullName,
            email,
            department,
            role.name(),
            userId
        );
    }

    public int disableUser(long userId) {
        return jdbcTemplate.update(DISABLE_USER, userId);
    }

    public int enableUser(long userId) {
        return jdbcTemplate.update(ENABLE_USER, userId);
    }

    public int updatePassword(long userId, String passwordHash,
        boolean mustChangePassword) {
        if (userId <= 0 || passwordHash == null) {
            throw new IllegalArgumentException(
                "password update values must not be null"
            );
        }
        return jdbcTemplate.update(UPDATE_PASSWORD, passwordHash,
            mustChangePassword, userId);
    }

    public void updateProfile(long userId, UserProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        int rows = switch (profile) {
            case StudentProfile student -> jdbcTemplate.update(
                UPDATE_STUDENT_PROFILE,
                student.getRollNumber(),
                student.getSemester(),
                student.getBatch(),
                userId
            );
            case OrganizerProfile organizer -> jdbcTemplate.update(
                UPDATE_ORGANIZER_PROFILE,
                organizer.getDesignation(),
                userId
            );
            case FacultyProfile faculty -> jdbcTemplate.update(
                UPDATE_FACULTY_PROFILE,
                faculty.getEmployeeId(),
                faculty.getDesignation(),
                userId
            );
            case AdminProfile admin -> jdbcTemplate.update(
                UPDATE_ADMIN_PROFILE,
                admin.getAdminLevel(),
                userId
            );
        };
        if (rows != 1) {
            throw new IllegalStateException(
                "Profile update did not affect a row for user " + userId
            );
        }
    }

    public void deleteProfile(long userId, Role role) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        String sql = switch (role) {
            case STUDENT -> DELETE_STUDENT_PROFILE;
            case ORGANIZER -> DELETE_ORGANIZER_PROFILE;
            case FACULTY -> DELETE_FACULTY_PROFILE;
            case ADMIN -> DELETE_ADMIN_PROFILE;
        };
        int rows = jdbcTemplate.update(sql, userId);
        if (rows != 1) {
            throw new IllegalStateException(
                "Profile delete did not affect a row for user " + userId
            );
        }
    }

    public boolean hasStudentHistory(long userId) {
        Integer matches = jdbcTemplate.queryForObject(
            STUDENT_HISTORY_EXISTS,
            Integer.class,
            userId,
            userId,
            userId
        );
        return matches != null && matches > 0;
    }

    static UserAccount mapAccount(ResultSet resultSet) throws SQLException {
        return new UserAccount(
            resultSet.getLong("id"),
            resultSet.getString("full_name"),
            resultSet.getString("email"),
            resultSet.getString("password_hash"),
            resultSet.getString("department"),
            parseRole(resultSet.getString("role")),
            resultSet.getBoolean("enabled"),
            resultSet.getBoolean("must_change_password")
        );
    }

    static UserSecurityState mapSecurityState(ResultSet resultSet)
        throws SQLException {
        return new UserSecurityState(
            resultSet.getLong("id"),
            parseRole(resultSet.getString("role")),
            resultSet.getBoolean("enabled")
        );
    }

    static AdminUserRow mapAdminUserRow(ResultSet resultSet)
        throws SQLException {
        long id = resultSet.getLong("id");
        Role role = parseRole(resultSet.getString("role"));
        boolean hasStudentProfile =
            resultSet.getBoolean("has_student_profile");
        boolean hasOrganizerProfile =
            resultSet.getBoolean("has_organizer_profile");
        boolean hasFacultyProfile =
            resultSet.getBoolean("has_faculty_profile");
        boolean hasAdminProfile =
            resultSet.getBoolean("has_admin_profile");
        boolean profileMatchesRole = switch (role) {
            case STUDENT -> hasStudentProfile
                && !hasOrganizerProfile
                && !hasFacultyProfile
                && !hasAdminProfile;
            case ORGANIZER -> hasOrganizerProfile
                && !hasStudentProfile
                && !hasFacultyProfile
                && !hasAdminProfile;
            case FACULTY -> hasFacultyProfile
                && !hasStudentProfile
                && !hasOrganizerProfile
                && !hasAdminProfile;
            case ADMIN -> hasAdminProfile
                && !hasStudentProfile
                && !hasOrganizerProfile
                && !hasFacultyProfile;
        };
        if (!profileMatchesRole) {
            throw new IllegalStateException(
                "Role/profile mismatch for user " + id
            );
        }

        UserProfile profile = switch (role) {
            case STUDENT -> new StudentProfile(
                resultSet.getString("roll_number"),
                resultSet.getInt("semester"),
                resultSet.getString("batch")
            );
            case ORGANIZER -> new OrganizerProfile(
                resultSet.getString("organizer_designation")
            );
            case FACULTY -> new FacultyProfile(
                resultSet.getString("employee_id"),
                resultSet.getString("faculty_designation")
            );
            case ADMIN -> new AdminProfile(
                resultSet.getInt("admin_level")
            );
        };
        return new AdminUserRow(
            id,
            resultSet.getString("full_name"),
            resultSet.getString("email"),
            resultSet.getString("department"),
            role,
            resultSet.getBoolean("enabled"),
            resultSet.getString("password_hash"),
            profile
        );
    }

    private static SearchSql buildAdminUserSearch(AdminUserListQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> arguments = new ArrayList<>();
        if (query.role() != null) {
            where.append(" AND u.role = ?");
            arguments.add(query.role().name());
        }
        if (query.enabled() != null) {
            where.append(" AND u.enabled = ?");
            arguments.add(query.enabled());
        }
        if (query.q() != null) {
            where.append(
                " AND (LOCATE(LOWER(?), LOWER(u.full_name)) > 0"
                    + " OR LOCATE(LOWER(?), LOWER(u.email)) > 0)"
            );
            arguments.add(query.q());
            arguments.add(query.q());
        }
        return new SearchSql(where.toString(), List.copyOf(arguments));
    }

    static Role parseRole(String rawRole) {
        if (rawRole == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        try {
            return Role.valueOf(rawRole);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "Unknown role: " + rawRole,
                exception
            );
        }
    }

    private record SearchSql(String where, List<Object> arguments) {}
}
