package edu.mec.studentportal.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LoginRequestTest {
    private static final Validator VALIDATOR =
        Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsNonNullNonBlankCredentials() {
        assertThat(VALIDATOR.validate(
            new LoginRequest("student@example.edu", "demo-password")
        )).isEmpty();
    }

    @Test
    void rejectsNullEmail() {
        assertThat(invalidProperties(new LoginRequest(null, "demo-password")))
            .containsExactly("email");
    }

    @Test
    void rejectsBlankEmail() {
        assertThat(invalidProperties(new LoginRequest("   ", "demo-password")))
            .containsExactly("email");
    }

    @Test
    void rejectsNullPassword() {
        assertThat(invalidProperties(
            new LoginRequest("student@example.edu", null)
        )).containsExactly("password");
    }

    @Test
    void rejectsBlankPassword() {
        assertThat(invalidProperties(
            new LoginRequest("student@example.edu", "   ")
        )).containsExactly("password");
    }

    @Test
    void doesNotNormalizeEmailOrPassword() {
        LoginRequest request = new LoginRequest(
            "  Student@Example.EDU  ",
            " demo-password "
        );

        assertThat(request.email()).isEqualTo("  Student@Example.EDU  ");
        assertThat(request.password()).isEqualTo(" demo-password ");
    }

    private static Set<String> invalidProperties(LoginRequest request) {
        return VALIDATOR.validate(request)
            .stream()
            .map(ConstraintViolation::getPropertyPath)
            .map(Object::toString)
            .collect(java.util.stream.Collectors.toSet());
    }
}
