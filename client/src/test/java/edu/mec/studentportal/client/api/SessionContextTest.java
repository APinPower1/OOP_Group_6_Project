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
        session.setSession("jwt-token", new UserSummary(7, "A B", "a@b.edu", Role.STUDENT));
        assertTrue(session.isLoggedIn());
        assertEquals("jwt-token", session.getToken());
        assertEquals(Role.STUDENT, session.getRole());
        assertEquals("A B", session.getUser().fullName());
    }

    @Test
    void rejectsBlankTokenOrNullUser() {
        assertThrows(IllegalArgumentException.class,
            () -> session.setSession("  ", new UserSummary(1, "A", "a@b.edu", Role.ADMIN)));
        assertThrows(IllegalArgumentException.class,
            () -> session.setSession("token", null));
        assertFalse(session.isLoggedIn());
    }

    @Test
    void clearSessionIsIdempotentAndNotifiesOnce() {
        AtomicInteger notified = new AtomicInteger();
        session.addListener(s -> notified.incrementAndGet());
        session.setSession("jwt-token", new UserSummary(1, "A", "a@b.edu", Role.ADMIN));
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
        session.setSession("jwt-token", new UserSummary(1, "A", "a@b.edu", Role.ADMIN));
        session.removeListener(listener);
        session.clearSession();
        assertEquals(1, notified.get());
    }

    @Test
    void rejectsNullListener() {
        assertThrows(IllegalArgumentException.class, () -> session.addListener(null));
    }
}
