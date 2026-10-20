package edu.mec.studentportal.club;

import edu.mec.studentportal.api.ApiErrorCodes;
import edu.mec.studentportal.api.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns club management and membership use cases. Admins manage clubs (deactivate, never hard
 * delete); students join/leave their own membership. Each (club_id, student_id) pair keeps a
 * single row: a prior LEFT row is reactivated on rejoin, so duplicates are impossible and the
 * unique constraint is the final guard. Joins lock the club row so the active flag and the
 * membership write stay consistent under concurrent joins/deactivation.
 *
 * <p>Club membership has no notification fan-out; {@code notifications} does not reference
 * clubs, so joins/leaves are intentionally not notified.
 */
@Service
public class ClubService {
    static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_ADMIN = "ADMIN";

    private final ClubDAO dao;
    private final Clock clock;

    @Autowired
    ClubService(ClubDAO dao) {
        this(dao, Clock.system(CAMPUS_ZONE));
    }

    ClubService(ClubDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /** Signed-in listing of active clubs with active-member counts. */
    @Transactional(readOnly = true)
    public List<ClubResponse> listActive(long requesterId) {
        requireActor(requesterId);
        return dao.findActive().stream()
            .map(club -> ClubResponse.from(club, dao.countActiveMembers(club.id())))
            .toList();
    }

    @Transactional(readOnly = true)
    public ClubResponse get(long clubId, long requesterId) {
        requireActor(requesterId);
        Club club = requireClub(clubId);
        return ClubResponse.from(club, dao.countActiveMembers(clubId));
    }

    @Transactional
    public ClubResponse create(ClubRequest request, long actingUserId) {
        requireAdmin(actingUserId);
        if (dao.nameExists(request.name(), null)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ClubErrorCodes.CLUB_NAME_TAKEN,
                "A club with this name already exists."
            );
        }
        long clubId;
        try {
            clubId = dao.insertClub(request.name(), request.description(), request.category());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ClubErrorCodes.CLUB_NAME_TAKEN,
                "A club with this name already exists."
            );
        }
        return ClubResponse.from(requireClub(clubId), 0);
    }

    @Transactional
    public ClubResponse update(long clubId, ClubRequest request, long actingUserId) {
        requireAdmin(actingUserId);
        Club club = requireClub(clubId);
        if (dao.nameExists(request.name(), clubId)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ClubErrorCodes.CLUB_NAME_TAKEN,
                "A club with this name already exists."
            );
        }
        try {
            dao.updateClub(clubId, request.name(), request.description(), request.category());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ClubErrorCodes.CLUB_NAME_TAKEN,
                "A club with this name already exists."
            );
        }
        Club updated = requireClub(club.id());
        return ClubResponse.from(updated, dao.countActiveMembers(clubId));
    }

    /** Admin deactivates a club; memberships and history are retained. */
    @Transactional
    public ClubResponse deactivate(long clubId, long actingUserId) {
        requireAdmin(actingUserId);
        Club club = requireClub(clubId);
        if (club.active()) {
            dao.setActive(clubId, false);
        }
        Club deactivated = requireClub(clubId);
        return ClubResponse.from(deactivated, dao.countActiveMembers(clubId));
    }

    /** Student joins or reactivates their own membership; inactive clubs cannot be joined. */
    @Transactional
    public MembershipResponse join(long clubId, long studentId) {
        requireStudent(studentId);
        Club club = dao.lockById(clubId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                ClubErrorCodes.CLUB_NOT_FOUND,
                "The club does not exist."
            )
        );
        if (!club.active()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ClubErrorCodes.CLUB_INACTIVE,
                "This club is inactive and cannot be joined."
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Optional<Membership> existing = dao.findMembership(clubId, studentId);
        if (existing.isEmpty()) {
            long membershipId;
            try {
                membershipId = dao.insertMembership(clubId, studentId, now);
            } catch (DuplicateKeyException exception) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    ApiErrorCodes.CONFLICT,
                    "Membership already exists."
                );
            }
            return new MembershipResponse(membershipId, clubId, MembershipStatus.ACTIVE, now);
        }

        Membership membership = existing.get();
        if (membership.status() == MembershipStatus.ACTIVE) {
            // Idempotent: joining an already-active membership returns the current state.
            return MembershipResponse.from(membership);
        }
        dao.reactivateMembership(membership.id(), now);
        return new MembershipResponse(membership.id(), clubId, MembershipStatus.ACTIVE, now);
    }

    /** Student leaves their own club; status becomes LEFT, the row is retained. */
    @Transactional
    public MembershipResponse leave(long clubId, long studentId) {
        requireStudent(studentId);
        requireClub(clubId);
        Membership membership = dao.findMembership(clubId, studentId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                ClubErrorCodes.NOT_A_MEMBER,
                "You are not a member of this club."
            )
        );
        if (membership.status() == MembershipStatus.LEFT) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ClubErrorCodes.NOT_A_MEMBER,
                "You are not an active member of this club."
            );
        }
        dao.leaveMembership(membership.id());
        return new MembershipResponse(
            membership.id(),
            membership.clubId(),
            MembershipStatus.LEFT,
            membership.joinedAt()
        );
    }

    /** Admin-only roster with minimal student data. */
    @Transactional(readOnly = true)
    public List<ClubMemberResponse> members(long clubId, long actingUserId) {
        requireAdmin(actingUserId);
        requireClub(clubId);
        return dao.findMembers(clubId).stream()
            .map(row -> new ClubMemberResponse(
                row.membershipId(),
                row.studentId(),
                row.fullName(),
                row.rollNumber(),
                row.status(),
                row.joinedAt()
            ))
            .toList();
    }

    private Club requireClub(long clubId) {
        return dao.findById(clubId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                ClubErrorCodes.CLUB_NOT_FOUND,
                "The club does not exist."
            )
        );
    }

    private ClubDAO.ActorRow requireActor(long userId) {
        ClubDAO.ActorRow actor = dao.findActor(userId).orElseThrow(() ->
            new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to access clubs."
            )
        );
        if (!actor.enabled()) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "You are not permitted to access clubs."
            );
        }
        return actor;
    }

    private void requireAdmin(long userId) {
        ClubDAO.ActorRow actor = requireActor(userId);
        if (!ROLE_ADMIN.equals(actor.role())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "Only admins may manage clubs."
            );
        }
    }

    private void requireStudent(long userId) {
        ClubDAO.ActorRow actor = requireActor(userId);
        if (!ROLE_STUDENT.equals(actor.role())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCodes.FORBIDDEN,
                "Only students may manage their club membership."
            );
        }
    }
}
