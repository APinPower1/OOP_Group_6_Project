package edu.mec.studentportal.registration;

import static org.assertj.core.api.Assertions.assertThat;

import edu.mec.studentportal.api.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

/**
 * Concurrency safety: simultaneous requests are serialized by the SELECT ... FOR UPDATE
 * event-row lock, and the database unique constraint is the last line of defence.
 * Pool size is raised to 30 in the test properties so every contender gets a connection.
 */
@EnabledIf(
    value = "edu.mec.studentportal.registration.RegistrationTestDatabase#isAvailable",
    disabledReason = "MySQL test database unreachable; set TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD"
)
class RegistrationConcurrencyTest extends RegistrationIntegrationTestSupport {
    private static final int POOL_THREADS = 16;

    @Autowired
    RegistrationDAO registrationDao;

    @Test
    void concurrentLastSeatRequestsNeverOverbook() throws Exception {
        int capacity = 3;
        int contenders = 12;
        long event = createApprovedEvent(createOrganizer(), capacity);
        List<Long> students = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            students.add(createStudent());
        }

        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger eventFull = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();
        runConcurrently(
            students,
            student -> {
                registrationService.register(event, student);
                confirmed.incrementAndGet();
            },
            error -> {
                if (isEventFull(error)) {
                    eventFull.incrementAndGet();
                } else {
                    unexpected.add(error);
                }
            }
        );

        assertThat(unexpected).isEmpty();
        assertThat(confirmed.get()).isEqualTo(capacity);
        assertThat(eventFull.get()).isEqualTo(contenders - capacity);
        assertConfirmedNeverExceedsCapacity(event, capacity);
        assertConfirmedCount(event, capacity);
    }

    @Test
    void concurrentSameStudentRequestsCreateExactlyOneRegistration() throws Exception {
        int attempts = 8;
        long event = createApprovedEvent(createOrganizer(), 10);
        long student = createStudent();
        List<Long> identicalRequests = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            identicalRequests.add(student);
        }

        AtomicInteger duplicates = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();
        runConcurrently(
            identicalRequests,
            requestId -> registrationService.register(event, student),
            error -> {
                if (isDuplicate(error)) {
                    duplicates.incrementAndGet();
                } else {
                    unexpected.add(error);
                }
            }
        );

        assertThat(unexpected).isEmpty();
        assertThat(duplicates.get()).isEqualTo(attempts - 1);
        assertConfirmedCount(event, 1);
        Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM registrations WHERE student_id = ? AND event_id = ?",
            Integer.class,
            student,
            event
        );
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void cancellationAndCompetingRegistrationsNeverOverbook() throws Exception {
        int capacity = 1;
        long event = createApprovedEvent(createOrganizer(), capacity);
        long holder = createStudent();
        registrationService.register(event, holder);
        List<Long> tasks = new ArrayList<>();
        tasks.add(-1L);
        for (int i = 0; i < 6; i++) {
            tasks.add(createStudent());
        }

        AtomicInteger confirmed = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();
        runConcurrently(
            tasks,
            taskId -> {
                if (taskId == -1L) {
                    registrationService.cancel(event, holder);
                } else {
                    try {
                        registrationService.register(event, taskId);
                        confirmed.incrementAndGet();
                    } catch (ApiException apiException) {
                        assertThat(apiException.getCode()).isEqualTo(RegistrationErrorCodes.EVENT_FULL);
                    }
                }
            },
            unexpected::add
        );

        assertThat(unexpected).isEmpty();
        assertConfirmedNeverExceedsCapacity(event, capacity);
        assertConfirmedCount(event, confirmed.get());
    }

    @Test
    void uniqueConstraintRaceAtDaoLevelRaisesDuplicateKeyException() throws Exception {
        long event = createApprovedEvent(createOrganizer(), 10);
        long student = createStudent();
        int writers = 2;
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> duplicateKeys = new ArrayList<>();
        List<Throwable> unexpected = new ArrayList<>();

        runConcurrently(
            List.of(1L, 2L),
            writer -> {
                try {
                    registrationDao.insert(
                        student,
                        event,
                        "RACE-" + event + "-" + student + "-" + writer,
                        now()
                    );
                    successes.incrementAndGet();
                } catch (DuplicateKeyException exception) {
                    duplicateKeys.add(exception);
                }
            },
            unexpected::add
        );

        assertThat(unexpected).isEmpty();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(duplicateKeys).hasSize(1);
        assertConfirmedCount(event, 1);
    }

    private static boolean isEventFull(Throwable error) {
        return isApiCode(error, RegistrationErrorCodes.EVENT_FULL);
    }

    private static boolean isDuplicate(Throwable error) {
        return isApiCode(error, RegistrationErrorCodes.DUPLICATE_REGISTRATION);
    }

    private static boolean isApiCode(Throwable error, String code) {
        return error instanceof ApiException apiException && code.equals(apiException.getCode());
    }

    /** Starts all tasks at once; unexpected handler rejections propagate and fail the test. */
    private void runConcurrently(
        List<Long> requestIds,
        LongConsumer action,
        Consumer<Throwable> failureHandler
    ) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(POOL_THREADS, requestIds.size()));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long requestId : requestIds) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        action.accept(requestId);
                    } catch (Throwable error) {
                        synchronized (failureHandler) {
                            failureHandler.accept(error);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
