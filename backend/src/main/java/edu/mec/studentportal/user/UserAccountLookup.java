package edu.mec.studentportal.user;

import java.util.Optional;

/**
 * Narrow read contract used by the {@code auth} and {@code security} modules.
 * Those modules must depend on this interface rather than on {@link UserDao}.
 */
public interface UserAccountLookup {
    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findById(long userId);

    Optional<UserSecurityState> findSecurityStateById(long userId);
}
