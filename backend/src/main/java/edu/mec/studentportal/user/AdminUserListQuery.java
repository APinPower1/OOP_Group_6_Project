package edu.mec.studentportal.user;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import org.springframework.http.HttpStatus;

public record AdminUserListQuery(
    int page,
    int size,
    Role role,
    Boolean enabled,
    String q
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public AdminUserListQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                "size must be between 1 and " + MAX_SIZE
            );
        }
        if (q != null) {
            q = q.strip();
            if (q.isEmpty()) {
                q = null;
            }
        }
    }

    public static AdminUserListQuery from(
        String page,
        String size,
        String role,
        String enabled,
        String q
    ) {
        return new AdminUserListQuery(
            parsePage(page),
            parseSize(size),
            parseRole(role),
            parseEnabled(enabled),
            q
        );
    }

    private static int parsePage(String rawPage) {
        if (rawPage == null) {
            return DEFAULT_PAGE;
        }
        try {
            int page = Integer.parseInt(rawPage.strip());
            if (page < 0) {
                throw invalid("page must be 0 or greater.");
            }
            return page;
        } catch (NumberFormatException exception) {
            throw invalid("page must be a whole number.");
        }
    }

    private static int parseSize(String rawSize) {
        if (rawSize == null) {
            return DEFAULT_SIZE;
        }
        try {
            int size = Integer.parseInt(rawSize.strip());
            if (size < 1 || size > MAX_SIZE) {
                throw invalid(
                    "size must be between 1 and " + MAX_SIZE + "."
                );
            }
            return size;
        } catch (NumberFormatException exception) {
            throw invalid("size must be a whole number.");
        }
    }

    private static Role parseRole(String rawRole) {
        if (rawRole == null) {
            return null;
        }
        try {
            return Role.valueOf(rawRole.strip());
        } catch (IllegalArgumentException exception) {
            throw invalid(
                "role must be one of STUDENT, ORGANIZER, FACULTY, ADMIN."
            );
        }
    }

    private static Boolean parseEnabled(String rawEnabled) {
        if (rawEnabled == null) {
            return null;
        }
        String enabled = rawEnabled.strip();
        if ("true".equalsIgnoreCase(enabled)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(enabled)) {
            return Boolean.FALSE;
        }
        throw invalid("enabled must be true or false.");
    }

    private static ApiException invalid(String message) {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.INVALID_REQUEST,
            message
        );
    }
}
