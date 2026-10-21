package edu.mec.studentportal.user;

import com.fasterxml.jackson.databind.JsonNode;
import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;

/** Shared validation for admin account and role-profile request values. */
final class AdminUserValidation {
    static final int MAX_FULL_NAME_LENGTH = 120;
    static final int MAX_EMAIL_LENGTH = 254;
    static final int MAX_DEPARTMENT_LENGTH = 120;
    private static final int MAX_ROLL_NUMBER_LENGTH = 40;
    private static final int MAX_BATCH_LENGTH = 40;
    private static final int MAX_DESIGNATION_LENGTH = 100;
    private static final int MAX_EMPLOYEE_ID_LENGTH = 40;

    private static final Set<String> STUDENT_FIELDS =
        Set.of("rollNumber", "semester", "batch");
    private static final Set<String> ORGANIZER_FIELDS =
        Set.of("designation");
    private static final Set<String> FACULTY_FIELDS =
        Set.of("employeeId", "designation");
    private static final Set<String> ADMIN_FIELDS =
        Set.of("adminLevel");

    private AdminUserValidation() {}

    static String requireText(
        String value,
        String fieldName,
        int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + " is required.");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw invalid(
                fieldName + " must be at most " + maxLength
                    + " characters."
            );
        }
        return trimmed;
    }

    static String requireEmail(String value) {
        String email = requireText(value, "email", MAX_EMAIL_LENGTH);
        return UserService.normalizeEmail(email);
    }

    static UserProfile parseProfile(Role role, JsonNode node) {
        if (role == null) {
            throw invalid("role is required.");
        }
        if (node == null || !node.isObject()) {
            throw invalid("profile must be an object.");
        }
        return switch (role) {
            case STUDENT -> {
                requireOnlyFields(node, STUDENT_FIELDS, role);
                yield new StudentProfile(
                    requireProfileText(
                        node,
                        "rollNumber",
                        MAX_ROLL_NUMBER_LENGTH
                    ),
                    requireProfileInt(node, "semester", 1),
                    requireProfileText(node, "batch", MAX_BATCH_LENGTH)
                );
            }
            case ORGANIZER -> {
                requireOnlyFields(node, ORGANIZER_FIELDS, role);
                yield new OrganizerProfile(
                    requireProfileText(
                        node,
                        "designation",
                        MAX_DESIGNATION_LENGTH
                    )
                );
            }
            case FACULTY -> {
                requireOnlyFields(node, FACULTY_FIELDS, role);
                yield new FacultyProfile(
                    requireProfileText(
                        node,
                        "employeeId",
                        MAX_EMPLOYEE_ID_LENGTH
                    ),
                    requireProfileText(
                        node,
                        "designation",
                        MAX_DESIGNATION_LENGTH
                    )
                );
            }
            case ADMIN -> {
                requireOnlyFields(node, ADMIN_FIELDS, role);
                int adminLevel = node.has("adminLevel")
                    ? requireProfileInt(node, "adminLevel", 1)
                    : 1;
                yield new AdminProfile(adminLevel);
            }
        };
    }

    static ApiException invalid(String message) {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.INVALID_REQUEST,
            message
        );
    }

    private static void requireOnlyFields(
        JsonNode node,
        Set<String> allowedFields,
        Role role
    ) {
        Set<String> unsupportedFields = new TreeSet<>();
        node.fieldNames().forEachRemaining(fieldName -> {
            if (!allowedFields.contains(fieldName)) {
                unsupportedFields.add(fieldName);
            }
        });
        if (!unsupportedFields.isEmpty()) {
            throw invalid(
                "profile contains unsupported field(s) for " + role + ": "
                    + String.join(", ", unsupportedFields)
            );
        }
    }

    private static String requireProfileText(
        JsonNode node,
        String fieldName,
        int maxLength
    ) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw invalid("profile." + fieldName + " is required.");
        }
        if (!value.isTextual()) {
            throw invalid("profile." + fieldName + " must be a string.");
        }
        String text = value.textValue().strip();
        if (text.isEmpty()) {
            throw invalid("profile." + fieldName + " is required.");
        }
        if (text.length() > maxLength) {
            throw invalid(
                "profile." + fieldName + " must be at most " + maxLength
                    + " characters."
            );
        }
        return text;
    }

    private static int requireProfileInt(
        JsonNode node,
        String fieldName,
        int minimum
    ) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw invalid("profile." + fieldName + " is required.");
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid("profile." + fieldName + " must be an integer.");
        }
        int number = value.intValue();
        if (number < minimum) {
            throw invalid(
                "profile." + fieldName + " must be at least " + minimum + "."
            );
        }
        return number;
    }
}
