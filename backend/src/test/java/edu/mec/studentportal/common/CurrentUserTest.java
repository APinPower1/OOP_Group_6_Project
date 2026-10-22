package edu.mec.studentportal.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.mec.studentportal.api.ApiException;
import edu.mec.studentportal.user.Role;
import edu.mec.studentportal.user.UserSecurityState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class CurrentUserTest {
    @Test
    void resolvesIdFromProductionSecurityStatePrincipal() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            new UserSecurityState(41L, Role.ADMIN, true),
            null,
            List.of()
        );

        assertEquals(41L, CurrentUser.id(authentication));
    }

    @Test
    void resolvesIdFromStringPrincipalUsedByControllerTests() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            "42",
            null,
            List.of()
        );

        assertEquals(42L, CurrentUser.id(authentication));
    }

    @Test
    void rejectsMissingOrInvalidPrincipal() {
        var missingPrincipal = UsernamePasswordAuthenticationToken.authenticated(
            null,
            null,
            List.of()
        );
        var invalidPrincipal = UsernamePasswordAuthenticationToken.authenticated(
            "not-an-id",
            null,
            List.of()
        );

        assertThrows(ApiException.class, () -> CurrentUser.id(null));
        assertThrows(ApiException.class, () -> CurrentUser.id(missingPrincipal));
        assertThrows(ApiException.class, () -> CurrentUser.id(invalidPrincipal));
    }
}
