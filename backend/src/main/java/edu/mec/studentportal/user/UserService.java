package edu.mec.studentportal.user;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserAccountLookup {
    private static final String DUPLICATE_USER_MESSAGE =
        "A user with the same email or profile identifier already exists.";
    private static final String USER_NOT_FOUND_MESSAGE =
        "User was not found.";
    private static final String SELF_DEMOTION_MESSAGE =
        "An administrator cannot change their own role away from ADMIN.";
    private static final String LAST_ADMIN_MESSAGE =
        "The last enabled administrator cannot be demoted.";
    private static final String SELF_DISABLE_MESSAGE =
        "An administrator cannot disable their own account.";
    private static final String LAST_ENABLED_ADMIN_DISABLE_MESSAGE =
        "The last enabled administrator cannot be disabled.";
    private static final String STUDENT_HISTORY_MESSAGE =
        "A student with registration, club, or feedback history cannot "
            + "change role.";

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserDao userDao, PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
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

    public AdminUserPageResponse listUsers(AdminUserListQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        AdminUserSearchResult result = userDao.searchAdminUsers(query);
        List<AdminUserResponse> items = result.users().stream()
            .map(AdminUserResponse::from)
            .toList();
        int totalPages = (int) Math.ceilDiv(
            result.totalItems(),
            (long) query.size()
        );
        return new AdminUserPageResponse(
            items,
            query.page(),
            query.size(),
            result.totalItems(),
            totalPages
        );
    }

    @Transactional
    public AdminUserResponse createUser(AdminUserCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        String passwordHash = passwordEncoder.encode(command.password());
        try {
            long userId = userDao.insertUser(
                command.fullName(),
                command.email(),
                passwordHash,
                command.department(),
                command.role()
            );
            userDao.insertProfile(userId, command.profile());
            return new AdminUserResponse(
                userId,
                command.fullName(),
                command.email(),
                command.department(),
                command.role(),
                true,
                command.profile()
            );
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCodes.CONFLICT,
                DUPLICATE_USER_MESSAGE
            );
        }
    }

    @Transactional
    public AdminUserResponse updateUser(
        long userId,
        long actingAdminId,
        AdminUserUpdateCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        List<Long> enabledAdminIds =
            userDao.lockEnabledAdministratorIds();
        AdminUserRow existing = userDao.findAdminUserByIdForUpdate(userId)
            .orElseThrow(UserService::userNotFound);

        boolean roleChanged = existing.role() != command.role();
        if (roleChanged && existing.role() == Role.ADMIN) {
            if (userId == actingAdminId) {
                throw conflict(SELF_DEMOTION_MESSAGE);
            }
            if (existing.enabled() && enabledAdminIds.size() <= 1) {
                throw conflict(LAST_ADMIN_MESSAGE);
            }
        }
        if (roleChanged
            && existing.role() == Role.STUDENT
            && userDao.hasStudentHistory(userId)) {
            throw conflict(STUDENT_HISTORY_MESSAGE);
        }

        try {
            int updated = userDao.updateUser(
                userId,
                command.fullName(),
                command.email(),
                command.department(),
                command.role()
            );
            if (updated != 1) {
                throw new IllegalStateException(
                    "User update did not affect a row for user " + userId
                );
            }
            if (roleChanged) {
                userDao.deleteProfile(userId, existing.role());
                userDao.insertProfile(userId, command.profile());
            } else {
                userDao.updateProfile(userId, command.profile());
            }
        } catch (DuplicateKeyException exception) {
            throw conflict(DUPLICATE_USER_MESSAGE);
        }

        return new AdminUserResponse(
            userId,
            command.fullName(),
            command.email(),
            command.department(),
            command.role(),
            existing.enabled(),
            command.profile()
        );
    }

    @Transactional
    public AdminUserResponse disableUser(long userId, long actingAdminId) {
        List<Long> enabledAdminIds =
            userDao.lockEnabledAdministratorIds();
        AdminUserRow existing = userDao.findAdminUserByIdForUpdate(userId)
            .orElseThrow(UserService::userNotFound);

        if (userId == actingAdminId) {
            throw conflict(SELF_DISABLE_MESSAGE);
        }
        if (existing.enabled()
            && existing.role() == Role.ADMIN
            && enabledAdminIds.size() <= 1) {
            throw conflict(LAST_ENABLED_ADMIN_DISABLE_MESSAGE);
        }
        if (existing.enabled()) {
            int updated = userDao.disableUser(userId);
            if (updated != 1) {
                throw new IllegalStateException(
                    "User disable did not affect a row for user " + userId
                );
            }
        }

        return new AdminUserResponse(
            userId,
            existing.fullName(),
            existing.email(),
            existing.department(),
            existing.role(),
            false,
            existing.profile()
        );
    }

    @Transactional
    public AdminUserResponse enableUser(long userId) {
        userDao.lockEnabledAdministratorIds();
        AdminUserRow existing = userDao.findAdminUserByIdForUpdate(userId)
            .orElseThrow(UserService::userNotFound);

        if (!existing.enabled()) {
            int updated = userDao.enableUser(userId);
            if (updated != 1) {
                throw new IllegalStateException(
                    "User enable did not affect a row for user " + userId
                );
            }
        }

        return new AdminUserResponse(
            userId,
            existing.fullName(),
            existing.email(),
            existing.department(),
            existing.role(),
            true,
            existing.profile()
        );
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

    private static ApiException userNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCodes.NOT_FOUND,
            USER_NOT_FOUND_MESSAGE
        );
    }

    private static ApiException conflict(String message) {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCodes.CONFLICT,
            message
        );
    }
}
