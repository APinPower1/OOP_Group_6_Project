package edu.mec.studentportal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.mec.studentportal.user.Role;
import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Test;

class UserSummaryResponseTest {
    @Test
    void exposesPublicSummaryFields() {
        UserSummaryResponse summary = summary();

        assertThat(summary.id()).isEqualTo(41L);
        assertThat(summary.fullName()).isEqualTo("Demo Student");
        assertThat(summary.email()).isEqualTo("student@example.edu");
        assertThat(summary.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void recordComponentsContainOnlyPublicSummaryFields() {
        assertThat(UserSummaryResponse.class.getRecordComponents())
            .extracting(RecordComponent::getName)
            .containsExactly("id", "fullName", "email", "role");
    }

    @Test
    void rejectsNullFullName() {
        assertThatThrownBy(
            () -> new UserSummaryResponse(41L, null, "student@example.edu", Role.STUDENT)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("fullName must not be null");
    }

    @Test
    void rejectsNullEmail() {
        assertThatThrownBy(
            () -> new UserSummaryResponse(41L, "Demo Student", null, Role.STUDENT)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("email must not be null");
    }

    @Test
    void rejectsNullRole() {
        assertThatThrownBy(
            () -> new UserSummaryResponse(41L, "Demo Student", "student@example.edu", null)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("role must not be null");
    }

    private static UserSummaryResponse summary() {
        return new UserSummaryResponse(
            41L,
            "Demo Student",
            "student@example.edu",
            Role.STUDENT
        );
    }
}
