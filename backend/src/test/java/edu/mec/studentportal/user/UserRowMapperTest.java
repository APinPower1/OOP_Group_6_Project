package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UserRowMapperTest {
    @ParameterizedTest
    @EnumSource(Role.class)
    void parsesEachRole(Role role) {
        assertThat(UserDao.parseRole(role.name())).isEqualTo(role);
    }

    @Test
    void rejectsUnknownRole() {
        assertThatThrownBy(() -> UserDao.parseRole("PRINCIPAL"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown role: PRINCIPAL");
    }

    @Test
    void rejectsNullRole() {
        assertThatThrownBy(() -> UserDao.parseRole(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("role must not be null");
    }

    @Test
    void mapsUserAccountRow() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(42L);
        when(resultSet.getString("full_name")).thenReturn("Ayesha Khan");
        when(resultSet.getString("email")).thenReturn("ayesha@example.com");
        when(resultSet.getString("password_hash")).thenReturn("$2a$10$hash");
        when(resultSet.getString("department")).thenReturn("Computing");
        when(resultSet.getString("role")).thenReturn("ORGANIZER");
        when(resultSet.getBoolean("enabled")).thenReturn(false);

        assertThat(UserDao.mapAccount(resultSet)).isEqualTo(
            new UserAccount(
                42L,
                "Ayesha Khan",
                "ayesha@example.com",
                "$2a$10$hash",
                "Computing",
                Role.ORGANIZER,
                false
            )
        );
    }

    @Test
    void mapsSecurityStateRow() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(42L);
        when(resultSet.getString("role")).thenReturn("ADMIN");
        when(resultSet.getBoolean("enabled")).thenReturn(true);

        assertThat(UserDao.mapSecurityState(resultSet)).isEqualTo(
            new UserSecurityState(42L, Role.ADMIN, true)
        );
    }
}
