package edu.mec.studentportal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.UserSecurityState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CurrentUserTest {

    @Test
    void resolvesIdFromUserSecurityStatePrincipalUsedByJwtFilter() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            new UserSecurityState(42L, Role.STUDENT, true),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        assertThat(CurrentUser.id(authentication)).isEqualTo(42L);
    }

    @Test
    void resolvesIdFromNumericStringPrincipalUsedByTestAuthFilter() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            "41",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        assertThat(CurrentUser.id(authentication)).isEqualTo(41L);
    }

    @Test
    void rejectsNullAuthentication() {
        assertThatThrownBy(() -> CurrentUser.id(null))
            .isInstanceOf(ApiException.class)
            .hasMessage("Authentication is required.")
            .extracting(exception -> ((ApiException) exception).getStatus().value())
            .isEqualTo(401);
    }

    @Test
    void rejectsNonNumericPrincipalName() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            "not-a-number",
            null,
            List.of()
        );

        assertThatThrownBy(() -> CurrentUser.id(authentication))
            .isInstanceOf(ApiException.class)
            .extracting(exception -> ((ApiException) exception).getCode())
            .isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void rejectsAuthenticationWithoutPrincipal() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            null,
            null,
            List.of()
        );

        assertThatThrownBy(() -> CurrentUser.id(authentication))
            .isInstanceOf(ApiException.class);
    }
}
