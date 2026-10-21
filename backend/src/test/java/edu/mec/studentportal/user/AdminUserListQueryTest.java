package edu.mec.studentportal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class AdminUserListQueryTest {
    @Test
    void appliesDocumentedDefaults() {
        assertThat(AdminUserListQuery.from(null, null, null, null, null))
            .isEqualTo(new AdminUserListQuery(0, 20, null, null, null));
    }

    @Test
    void parsesAllSupportedParameters() {
        assertThat(AdminUserListQuery.from(
            "2",
            "50",
            "STUDENT",
            "true",
            "  Ayesha  "
        )).isEqualTo(
            new AdminUserListQuery(2, 50, Role.STUDENT, true, "Ayesha")
        );
    }

    @Test
    void acceptsMaximumPageSizeAndFalseEnabled() {
        assertThat(AdminUserListQuery.from("0", "100", "ADMIN", "false", null))
            .isEqualTo(new AdminUserListQuery(0, 100, Role.ADMIN, false, null));
    }

    @Test
    void treatsBlankSearchAsNoFilter() {
        assertThat(AdminUserListQuery.from(null, null, null, null, "   ").q())
            .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc", ""})
    void rejectsInvalidPage(String page) {
        assertInvalid(() -> AdminUserListQuery.from(
            page,
            null,
            null,
            null,
            null
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "abc", ""})
    void rejectsInvalidSize(String size) {
        assertInvalid(() -> AdminUserListQuery.from(
            null,
            size,
            null,
            null,
            null
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "student", ""})
    void rejectsInvalidRole(String role) {
        assertInvalid(() -> AdminUserListQuery.from(
            null,
            null,
            role,
            null,
            null
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "1", ""})
    void rejectsInvalidEnabled(String enabled) {
        assertInvalid(() -> AdminUserListQuery.from(
            null,
            null,
            null,
            enabled,
            null
        ));
    }

    private static void assertInvalid(ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown).isInstanceOf(ApiException.class);

        ApiException exception = (ApiException) thrown;
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getCode()).isEqualTo(
            ApiErrorCodes.INVALID_REQUEST
        );
    }
}
