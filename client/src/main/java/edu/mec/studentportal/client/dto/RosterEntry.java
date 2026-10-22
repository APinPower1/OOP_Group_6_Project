package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Minimal student identity plus registration state for a roster row. */
public record RosterEntry(
    long registrationId,
    long studentId,
    String fullName,
    String rollNumber,
    RegistrationStatus status,
    String ticketNo,
    LocalDateTime registeredAt
) {}
