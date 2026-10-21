package edu.mec.studentportal.club;

import java.time.LocalDateTime;

/** Minimal member identity for the Admin-only roster. */
public record ClubMemberResponse(
    long membershipId,
    long studentId,
    String fullName,
    String rollNumber,
    MembershipStatus status,
    LocalDateTime joinedAt
) {}
