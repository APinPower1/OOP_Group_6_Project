package edu.mec.studentportal.user;

import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserAccountLookup {
    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userDao.findByEmail(normalizeEmail(email));
    }

    @Override
    public Optional<UserAccount> findById(long userId) {
        if (userId <= 0) {
            return Optional.empty();
        }
        return userDao.findById(userId);
    }

    @Override
    public Optional<UserSecurityState> findSecurityStateById(long userId) {
        if (userId <= 0) {
            return Optional.empty();
        }
        return userDao.findSecurityStateById(userId);
    }

    /**
     * Trims surrounding whitespace and lowercases an email for lookup.
     */
    static String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
