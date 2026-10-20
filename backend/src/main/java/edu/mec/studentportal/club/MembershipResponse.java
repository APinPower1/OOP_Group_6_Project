package edu.mec.studentportal.club;

import java.time.LocalDateTime;

/** Current student's membership state for a club. */
public record MembershipResponse(
    long membershipId,
    long clubId,
    MembershipStatus status,
    LocalDateTime joinedAt
) {
    static MembershipResponse from(Membership membership) {
        return new MembershipResponse(
            membership.id(),
            membership.clubId(),
            membership.status(),
            membership.joinedAt()
        );
    }
}
