package edu.mec.studentportal.registration;

import java.time.LocalDateTime;

/** Minimal student identity plus registration state for an event roster entry. */
public record RosterEntryResponse(
    long registrationId,
    long studentId,
    String fullName,
    String rollNumber,
    RegistrationStatus status,
    String ticketNo,
    LocalDateTime registeredAt
) {}
