package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Minimal member identity for the Admin club roster. */
public record ClubMemberDto(
    long membershipId,
    long studentId,
    String fullName,
    String rollNumber,
    MembershipStatus status,
    LocalDateTime joinedAt
) {}
