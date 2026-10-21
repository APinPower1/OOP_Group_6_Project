package edu.mec.studentportal.club;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf("edu.mec.studentportal.testsupport.TestDatabaseAvailability#isAvailable")
class ClubServiceTest extends ClubIntegrationTestSupport {
    @Test
    void adminCreatesClub() {
        long admin = createAdmin();
        ClubResponse created = clubService.create(request("Robotics Club " + System.nanoTime()), admin);

        assertTrue(created.id() > 0);
        assertTrue(created.active());
        assertEquals(0, created.activeMemberCount());
        assertEquals(1, countRows("clubs", "id = ?", created.id()));
    }

    @Test
    void studentCannotCreateClub() {
        long student = createStudent();
        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.create(request("Chess Club " + System.nanoTime()), student)
        );
        assertEquals(ApiErrorCodes.FORBIDDEN, exception.getCode());
    }

    @Test
    void duplicateNameRejectedCaseExact() {
        long admin = createAdmin();
        String name = "Dramatics Club " + System.nanoTime();
        clubService.create(request(name), admin);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.create(request(name), admin)
        );
        assertEquals(ClubErrorCodes.CLUB_NAME_TAKEN, exception.getCode());
        assertEquals(1, countRows("clubs", "name = ?", name));
    }

    @Test
    void updateClubRenamesAndKeepsId() {
        long admin = createAdmin();
        ClubResponse created = clubService.create(request("Music Club " + System.nanoTime()), admin);

        ClubResponse updated = clubService.update(
            created.id(),
            new ClubRequest("Music Society", "Updated.", "CULTURAL"),
            admin
        );

        assertEquals(created.id(), updated.id());
        assertEquals("Music Society", updated.name());
        assertEquals("CULTURAL", updated.category());
    }

    @Test
    void updateToExistingNameRejected() {
        long admin = createAdmin();
        String alpha = "Alpha " + System.nanoTime();
        clubService.create(request(alpha), admin);
        ClubResponse second = clubService.create(request("Beta " + System.nanoTime()), admin);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.update(second.id(), request(alpha), admin)
        );
        assertEquals(ClubErrorCodes.CLUB_NAME_TAKEN, exception.getCode());
    }

    @Test
    void deactivateRemovesFromActiveListingButRetainsMembers() {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Film Club " + System.nanoTime()), admin);
        clubService.join(club.id(), student);

        ClubResponse deactivated = clubService.deactivate(club.id(), admin);

        assertFalse(deactivated.active());
        assertEquals(1, deactivated.activeMemberCount());
        assertEquals(1, countRows("club_members", "club_id = ?", club.id()));
        assertTrue(clubService.listActive(admin).stream().noneMatch(c -> c.id() == club.id()));
    }

    @Test
    void studentJoinsActiveClub() {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Debate Club " + System.nanoTime()), admin);

        MembershipResponse membership = clubService.join(club.id(), student);

        assertEquals(MembershipStatus.ACTIVE, membership.status());
        assertEquals(club.id(), membership.clubId());
        assertEquals(1, countRows(
            "club_members",
            "club_id = ? AND student_id = ? AND status = 'ACTIVE'",
            club.id(),
            student
        ));
    }

    @Test
    void joiningTwiceIsIdempotentAndKeepsSingleRow() {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Astronomy Club " + System.nanoTime()), admin);

        MembershipResponse first = clubService.join(club.id(), student);
        MembershipResponse second = clubService.join(club.id(), student);

        assertEquals(first.membershipId(), second.membershipId());
        assertEquals(1, countRows("club_members", "club_id = ? AND student_id = ?", club.id(), student));
    }

    @Test
    void rejoinAfterLeaveReactivatesSameRow() {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Cycling Club " + System.nanoTime()), admin);

        MembershipResponse joined = clubService.join(club.id(), student);
        clubService.leave(club.id(), student);
        assertEquals(1, countRows(
            "club_members",
            "club_id = ? AND student_id = ? AND status = 'LEFT'",
            club.id(),
            student
        ));

        MembershipResponse rejoined = clubService.join(club.id(), student);

        assertEquals(joined.membershipId(), rejoined.membershipId());
        assertEquals(MembershipStatus.ACTIVE, rejoined.status());
        assertEquals(1, countRows("club_members", "club_id = ? AND student_id = ?", club.id(), student));
    }

    @Test
    void inactiveClubCannotBeJoined() {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Fencing Club " + System.nanoTime()), admin);
        clubService.deactivate(club.id(), admin);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.join(club.id(), student)
        );
        assertEquals(ClubErrorCodes.CLUB_INACTIVE, exception.getCode());
        assertEquals(0, countRows("club_members", "club_id = ?", club.id()));
    }

    @Test
    void joinUnknownClubNotFound() {
        long student = createStudent();
        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.join(999_999_999L, student)
        );
        assertEquals(ClubErrorCodes.CLUB_NOT_FOUND, exception.getCode());
    }

    @Test
    void adminCannotJoinAsMember() {
        long admin = createAdmin();
        ClubResponse club = clubService.create(request("Rowing Club " + System.nanoTime()), admin);
        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.join(club.id(), admin)
        );
        assertEquals(ApiErrorCodes.FORBIDDEN, exception.getCode());
    }

    @Test
    void leaveWithoutMembershipIsRejected() {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Ski Club " + System.nanoTime()), admin);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.leave(club.id(), student)
        );
        assertEquals(ClubErrorCodes.NOT_A_MEMBER, exception.getCode());
    }

    @Test
    void leaveTwiceIsRejectedSecondTime() {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Surf Club " + System.nanoTime()), admin);
        clubService.join(club.id(), student);
        clubService.leave(club.id(), student);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.leave(club.id(), student)
        );
        assertEquals(ClubErrorCodes.NOT_A_MEMBER, exception.getCode());
    }

    @Test
    void rosterIsAdminOnly() {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Baking Club " + System.nanoTime()), admin);
        clubService.join(club.id(), student);

        List<ClubMemberResponse> roster = clubService.members(club.id(), admin);
        assertEquals(1, roster.size());
        assertEquals(student, roster.get(0).studentId());
        assertNotNull(roster.get(0).fullName());
        assertNotNull(roster.get(0).rollNumber());

        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.members(club.id(), student)
        );
        assertEquals(ApiErrorCodes.FORBIDDEN, exception.getCode());
    }

    @Test
    void disabledUserCannotList() {
        long admin = createAdmin();
        ClubResponse club = clubService.create(request("Hidden Club " + System.nanoTime()), admin);
        long disabled = createUser("STUDENT", false);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> clubService.get(club.id(), disabled)
        );
        assertEquals(ApiErrorCodes.FORBIDDEN, exception.getCode());
    }

    @Test
    void concurrentJoinsProduceSingleMembershipRow() throws Exception {
        long admin = createAdmin();
        ClubResponse club = clubService.create(request("Math Club " + System.nanoTime()), admin);
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                long student = createStudent();
                executor.submit(() -> {
                    try {
                        startGate.await();
                        clubService.join(club.id(), student);
                    } catch (Exception exception) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            startGate.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent joins did not finish");

            assertEquals(0, failures.get(), "no join should fail");
            assertEquals(threads, countRows(
                "club_members",
                "club_id = ? AND status = 'ACTIVE'",
                club.id()
            ));
            assertEquals(threads, clubService.get(club.id(), admin).activeMemberCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameStudentConcurrentJoinsKeepOneRow() throws Exception {
        long admin = createAdmin();
        long student = createStudent();
        ClubResponse club = clubService.create(request("Race Club " + System.nanoTime()), admin);
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        clubService.join(club.id(), student);
                    } catch (Exception ignored) {
                        // reactivation/duplicate errors are acceptable; the row count is what matters
                    } finally {
                        done.countDown();
                    }
                });
            }
            startGate.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent joins did not finish");

            assertEquals(1, countRows("club_members", "club_id = ? AND student_id = ?", club.id(), student));
            assertEquals(1, clubService.get(club.id(), admin).activeMemberCount());
        } finally {
            executor.shutdownNow();
        }
    }
}
