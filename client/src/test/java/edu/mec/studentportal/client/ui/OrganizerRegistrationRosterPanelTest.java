package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.mec.studentportal.client.api.ClientConfig;
import edu.mec.studentportal.client.api.HttpApiClient;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.EventStatus;
import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.UserSummary;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrganizerRegistrationRosterPanelTest {
    private static final String ROSTER_RESPONSE = """
        {"eventId":12,"capacity":40,"registeredCount":1,"availableSlots":39,
         "registrations":[{"registrationId":71,"studentId":8,"fullName":"Ayesha Khan",
          "rollNumber":"CS-08","status":"CONFIRMED","ticketNo":"T-71",
          "registeredAt":"2026-10-24T10:30:00"}]}""";

    private HttpServer server;
    private SessionContext session;
    private HttpApiClient api;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = ROSTER_RESPONSE;
    private volatile CountDownLatch requestReceived = new CountDownLatch(0);
    private volatile CountDownLatch releaseResponse = new CountDownLatch(0);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        session = new SessionContext();
        session.setSession("test-token",
            new UserSummary(101, "Organizer One", "organizer@mec.edu", Role.ORGANIZER));
        api = new HttpApiClient(new ClientConfig(
            "http://127.0.0.1:" + server.getAddress().getPort(), 2000, 5
        ), session);
    }

    @AfterEach
    void stopServer() {
        releaseResponse.countDown();
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        lastPath.set(exchange.getRequestURI().toString());
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestReceived.countDown();
        try {
            releaseResponse.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    @Test
    void loadsAuthenticatedRosterAndDisplaysStudentDetails() throws Exception {
        releaseResponse.countDown();
        OrganizerRegistrationRosterPanel panel = createPanel(session);

        onEdt(() -> panel.loadButton().doClick());

        assertTrue(requestReceived.await(5, TimeUnit.SECONDS), "roster request was not sent");
        awaitStatus(panel, "1 registration(s) shown.");
        assertEquals("/api/events/12/registrations", lastPath.get());
        assertNotNull(lastAuthorization.get());
        assertTrue(lastAuthorization.get().startsWith("Bearer "));
        assertEquals(1, onEdt(() -> panel.rosterTable().getRowCount()));
        assertEquals("Ayesha Khan",
            onEdt(() -> panel.rosterTable().getValueAt(0, 2)));
        assertEquals("CS-08", onEdt(() -> panel.rosterTable().getValueAt(0, 3)));
        assertTrue(onEdt(() -> panel.summaryLabel().getText()).contains("39 available"));
    }

    @Test
    void showsEmptyRosterState() throws Exception {
        responseBody = """
            {"eventId":12,"capacity":40,"registeredCount":0,"availableSlots":40,
             "registrations":[]}""";
        releaseResponse.countDown();
        OrganizerRegistrationRosterPanel panel = createPanel(session);

        onEdt(() -> panel.loadButton().doClick());

        assertTrue(requestReceived.await(5, TimeUnit.SECONDS));
        awaitStatus(panel, "No registrations yet.");
        assertEquals(0, onEdt(() -> panel.rosterTable().getRowCount()));
        assertTrue(onEdt(() -> panel.summaryLabel().getText()).contains("40 available"));
    }

    @Test
    void displaysErrorAndRetryLoadsRoster() throws Exception {
        responseStatus = 403;
        responseBody = """
            {"status":403,"code":"FORBIDDEN","message":"Event is not yours."}""";
        releaseResponse.countDown();
        OrganizerRegistrationRosterPanel panel = createPanel(session);

        onEdt(() -> panel.loadButton().doClick());

        assertTrue(requestReceived.await(5, TimeUnit.SECONDS));
        awaitStatus(panel, "Could not load roster:");
        assertEquals("Retry", onEdt(() -> panel.loadButton().getText()));
        assertTrue(onEdt(() -> panel.loadButton().isEnabled()));

        responseStatus = 200;
        responseBody = ROSTER_RESPONSE;
        requestReceived = new CountDownLatch(1);
        onEdt(() -> panel.loadButton().doClick());

        assertTrue(requestReceived.await(5, TimeUnit.SECONDS), "retry request was not sent");
        awaitStatus(panel, "1 registration(s) shown.");
        assertEquals(2, requestCount.get());
        assertEquals(1, onEdt(() -> panel.rosterTable().getRowCount()));
    }

    @Test
    void onlyListsEventsOwnedBySignedInOrganizer() throws Exception {
        OrganizerRegistrationRosterPanel panel = createPanel(session);
        EventDto owned = event(12, 101);
        EventDto notOwned = event(13, 202);

        onEdt(() -> panel.setEvents(List.of(owned, notOwned)));

        assertEquals(1, onEdt(() -> panel.eventSelector().getItemCount()));
        assertEquals(12L, onEdt(() ->
            panel.eventSelector().getItemAt(0).id()));
        assertTrue(onEdt(() -> panel.loadButton().isEnabled()));

        SessionContext studentSession = new SessionContext();
        studentSession.setSession("student-token",
            new UserSummary(101, "Student One", "student@mec.edu", Role.STUDENT));
        OrganizerRegistrationRosterPanel studentPanel = createPanel(studentSession);
        onEdt(() -> studentPanel.setEvents(List.of(owned)));
        assertEquals(0, onEdt(() -> studentPanel.eventSelector().getItemCount()));
        assertFalse(onEdt(() -> studentPanel.loadButton().isEnabled()));
    }

    @Test
    void preventsDuplicateRequestsWhileLoading() throws Exception {
        requestReceived = new CountDownLatch(1);
        releaseResponse = new CountDownLatch(1);
        OrganizerRegistrationRosterPanel panel = createPanel(session);

        onEdt(() -> panel.loadButton().doClick());
        assertTrue(requestReceived.await(5, TimeUnit.SECONDS), "request did not start");
        onEdt(() -> panel.loadButton().doClick());

        assertEquals("Loading registration roster...",
            onEdt(() -> panel.statusLabel().getText()));
        assertEquals(1, requestCount.get());
        assertFalse(onEdt(() -> panel.loadButton().isEnabled()));
        releaseResponse.countDown();
        awaitStatus(panel, "1 registration(s) shown.");
        assertEquals(1, requestCount.get());
    }

    private OrganizerRegistrationRosterPanel createPanel(SessionContext session)
        throws Exception {
        AtomicReference<OrganizerRegistrationRosterPanel> reference = new AtomicReference<>();
        onEdt(() -> {
            OrganizerRegistrationRosterPanel panel =
                new OrganizerRegistrationRosterPanel(api, session);
            panel.setEvents(List.of(event(12, 101)));
            reference.set(panel);
        });
        return reference.get();
    }

    private static EventDto event(long id, long ownerId) {
        return new EventDto(
            id, "Campus Fest", "Description", "Main Hall",
            LocalDateTime.of(2026, 11, 1, 10, 0),
            LocalDateTime.of(2026, 11, 1, 16, 0),
            40, EventStatus.APPROVED, ownerId, null, null, null, null, 0, 40
        );
    }

    private static void awaitStatus(OrganizerRegistrationRosterPanel panel, String expected)
        throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (onEdt(() -> panel.statusLabel().getText()).startsWith(expected)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for status: " + expected
            + "; actual: " + onEdt(() -> panel.statusLabel().getText()));
    }

    private static <T> T onEdt(EdtCallable<T> callable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return callable.call();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(callable.call());
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }

    private static void onEdt(EdtRunnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @FunctionalInterface
    private interface EdtCallable<T> {
        T call() throws Exception;
    }

    @FunctionalInterface
    private interface EdtRunnable {
        void run() throws Exception;
    }
}
