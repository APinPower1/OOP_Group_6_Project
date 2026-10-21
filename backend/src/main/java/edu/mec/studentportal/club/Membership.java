package edu.mec.studentportal.club;

import java.time.LocalDateTime;

/** Immutable membership domain object for one (club, student) pair. */
public record Membership(
    long id,
    long clubId,
    long studentId,
    LocalDateTime joinedAt,
    MembershipStatus status
) {}
