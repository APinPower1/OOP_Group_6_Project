package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserDao userDao;

    @InjectMocks
    private UserService userService;

    @Test
    void normalizesEmailBeforeLookup() {
        UserAccount account = account();
        when(userDao.findByEmail("alice@example.com"))
            .thenReturn(Optional.of(account));

        assertThat(userService.findByEmail("  Alice@Example.COM  "))
            .contains(account);
        verify(userDao).findByEmail("alice@example.com");
    }

    @Test
    void returnsEmptyForNullEmailWithoutQuerying() {
        assertThat(userService.findByEmail(null)).isEmpty();
        verifyNoInteractions(userDao);
    }

    @Test
    void returnsEmptyForBlankEmailWithoutQuerying() {
        assertThat(userService.findByEmail("   ")).isEmpty();
        verifyNoInteractions(userDao);
    }

    @Test
    void returnsEmptyWhenEmailIsUnknown() {
        when(userDao.findByEmail("nobody@example.com"))
            .thenReturn(Optional.empty());

        assertThat(userService.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void normalizeEmailTrimsAndLowerCases() {
        assertThat(UserService.normalizeEmail(" Alice@Example.COM "))
            .isEqualTo("alice@example.com");
    }

    @Test
    void normalizeEmailRejectsNull() {
        assertThatThrownBy(() -> UserService.normalizeEmail(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("email must not be null");
    }

    @Test
    void rejectsNonPositiveUserIdOnFindById() {
        assertThat(userService.findById(0)).isEmpty();
        assertThat(userService.findById(-1)).isEmpty();
        verifyNoInteractions(userDao);
    }

    @Test
    void delegatesFindById() {
        UserAccount account = account();
        when(userDao.findById(7L)).thenReturn(Optional.of(account));

        assertThat(userService.findById(7L)).contains(account);
    }

    @Test
    void rejectsNonPositiveUserIdOnSecurityStateLookup() {
        assertThat(userService.findSecurityStateById(0)).isEmpty();
        verifyNoInteractions(userDao);
    }

    @Test
    void delegatesSecurityStateLookup() {
        UserSecurityState state = new UserSecurityState(
            7L,
            Role.STUDENT,
            true
        );
        when(userDao.findSecurityStateById(7L)).thenReturn(Optional.of(state));

        assertThat(userService.findSecurityStateById(7L)).contains(state);
    }

    private static UserAccount account() {
        return new UserAccount(
            7L,
            "Alice Khan",
            "alice@example.com",
            "$2a$10$0123456789012345678901234567890123456789012345678901",
            "Computing",
            Role.STUDENT,
            true
        );
    }
}
