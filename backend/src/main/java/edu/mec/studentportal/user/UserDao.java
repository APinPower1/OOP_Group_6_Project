package edu.mec.studentportal.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserDao {
    private static final String SELECT_USER_COLUMNS = """
        SELECT id, full_name, email, password_hash, department, role, enabled
        FROM users
        """;

    private static final RowMapper<UserAccount> USER_ACCOUNT_MAPPER =
        (resultSet, rowNumber) -> mapAccount(resultSet);
    private static final RowMapper<UserSecurityState> USER_SECURITY_STATE_MAPPER =
        (resultSet, rowNumber) -> mapSecurityState(resultSet);

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

    static UserAccount mapAccount(ResultSet resultSet) throws SQLException {
        return new UserAccount(
            resultSet.getLong("id"),
            resultSet.getString("full_name"),
            resultSet.getString("email"),
            resultSet.getString("password_hash"),
            resultSet.getString("department"),
            parseRole(resultSet.getString("role")),
            resultSet.getBoolean("enabled")
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
}
