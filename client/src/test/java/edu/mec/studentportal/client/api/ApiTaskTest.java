package edu.mec.studentportal.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

/**
 * ApiTask: runs off the EDT, disables triggering components during flight, re-enables them
 * afterwards, and always funnels failures through {@link ApiException}.
 */
class ApiTaskTest {

    @Test
    void runsOffCallerThreadAndManagesComponentState() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Thread> callerThread = new AtomicReference<>(Thread.currentThread());
        AtomicReference<Thread> successThread = new AtomicReference<>();
        JButton trigger = new JButton();

        ApiTask.run(
            () -> {
                started.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    fail("test never released the call");
                }
                successThread.set(Thread.currentThread());
                return "payload";
            },
            value -> {
                result.set(value);
                finished.countDown();
            },
            error -> fail("unexpected error: " + error.getMessage()),
            trigger
        );

        assertTrue(started.await(5, TimeUnit.SECONDS), "call did not start");
        assertFalse(trigger.isEnabled(), "component must be disabled during flight");

        release.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS), "success callback never fired");
        assertEquals("payload", result.get());
        assertFalse(callerThread.get().equals(successThread.get()),
            "callback must not run on the caller thread");

        awaitEnabled(trigger);
        assertTrue(trigger.isEnabled(), "component must be re-enabled after the call");
    }

    @Test
    void apiExceptionPropagatesUnwrapped() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<ApiException> received = new AtomicReference<>();

        ApiTask.run(
            () -> {
                throw new ApiException(403, "EVENT_NOT_YOURS", "denied");
            },
            value -> fail("must not succeed"),
            error -> {
                received.set(error);
                finished.countDown();
            }
        );

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals(403, received.get().getStatus());
        assertEquals("EVENT_NOT_YOURS", received.get().getCode());
        assertEquals("denied", received.get().getMessage());
    }

    @Test
    void unexpectedFailureBecomesNetworkErrorAndComponentIsReenabled() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<ApiException> received = new AtomicReference<>();
        JButton trigger = new JButton();

        ApiTask.run(
            () -> {
                throw new IllegalStateException("boom");
            },
            value -> fail("must not succeed"),
            error -> {
                received.set(error);
                finished.countDown();
            },
            trigger
        );

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals(ApiException.NETWORK_STATUS, received.get().getStatus());
        assertEquals(ApiException.NETWORK_ERROR, received.get().getCode());
        assertEquals("boom", received.get().getMessage());
        awaitEnabled(trigger);
    }

    private static void awaitEnabled(JButton component) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!component.isEnabled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }
}
