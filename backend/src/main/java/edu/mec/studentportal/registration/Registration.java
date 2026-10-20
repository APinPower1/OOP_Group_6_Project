package edu.mec.studentportal.registration;

import java.time.LocalDateTime;

/** Immutable registration domain object; no JDBC or framework dependencies. */
public record Registration(
    long id,
    long studentId,
    long eventId,
    LocalDateTime registeredAt,
    RegistrationStatus status,
    String ticketNo
) {}
