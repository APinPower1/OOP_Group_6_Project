package edu.mec.studentportal.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.UserSummary;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionContextTest {
    private SessionContext session;

    @BeforeEach
    void setUp() {
        session = new SessionContext();
    }

    @Test
    void startsLoggedOut() {
        assertFalse(session.isLoggedIn());
        assertNull(session.getToken());
        assertNull(session.getUser());
        assertNull(session.getRole());
    }

    @Test
    void storesSessionAndExposesRole() {
        session.setSession("jwt-token", new UserSummary(7, "A B", "a@b.edu", Role.STUDENT, false));
        assertTrue(session.isLoggedIn());
        assertEquals("jwt-token", session.getToken());
        assertEquals(Role.STUDENT, session.getRole());
        assertEquals("A B", session.getUser().fullName());
    }

    @Test
    void rejectsBlankTokenOrNullUser() {
        assertThrows(IllegalArgumentException.class,
            () -> session.setSession("  ", new UserSummary(1, "A", "a@b.edu", Role.ADMIN, false)));
        assertThrows(IllegalArgumentException.class,
            () -> session.setSession("token", null));
        assertFalse(session.isLoggedIn());
    }

    @Test
    void clearSessionIsIdempotentAndNotifiesOnce() {
        AtomicInteger notified = new AtomicInteger();
        session.addListener(s -> notified.incrementAndGet());
        session.setSession("jwt-token", new UserSummary(1, "A", "a@b.edu", Role.ADMIN, false));
        assertEquals(1, notified.get());

        session.clearSession();
        assertEquals(2, notified.get());
        session.clearSession();
        assertEquals(2, notified.get(), "clearing an empty session must not re-notify");
        assertFalse(session.isLoggedIn());
    }

    @Test
    void removedListenerIsNotNotified() {
        AtomicInteger notified = new AtomicInteger();
        SessionContext.Listener listener = s -> notified.incrementAndGet();
        session.addListener(listener);
        session.setSession("jwt-token", new UserSummary(1, "A", "a@b.edu", Role.ADMIN, false));
        session.removeListener(listener);
        session.clearSession();
        assertEquals(1, notified.get());
    }

    @Test
    void rejectsNullListener() {
        assertThrows(IllegalArgumentException.class, () -> session.addListener(null));
    }

    @Test
    void refreshUserReplacesUserWithoutFiringListeners() {
        session.setSession("jwt-token",
            new UserSummary(7, "A B", "a@b.edu", Role.STUDENT, true));
        AtomicInteger events = new AtomicInteger();
        session.addListener(s -> events.incrementAndGet());

        session.refreshUser(new UserSummary(7, "A B", "a@b.edu", Role.STUDENT, false));

        assertFalse(session.getUser().mustChangePassword());
        assertEquals(0, events.get(),
            "refreshUser must not rebuild the open dashboard");
    }

    @Test
    void refreshUserRequiresActiveSession() {
        assertThrows(IllegalStateException.class, () -> session.refreshUser(
            new UserSummary(1, "A", "a@b.edu", Role.ADMIN, false)));
    }
}
