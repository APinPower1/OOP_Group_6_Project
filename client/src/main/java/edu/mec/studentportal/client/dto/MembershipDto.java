package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Current student's membership state for a club. */
public record MembershipDto(
    long membershipId,
    long clubId,
    MembershipStatus status,
    LocalDateTime joinedAt
) {}
