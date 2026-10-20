package edu.mec.studentportal.registration;

import java.time.LocalDateTime;

/**
 * Registration response per docs/API_CONTRACT.md. {@code registeredCount} and
 * {@code availableSlots} are calculated from CONFIRMED rows (never a stored counter)
 * and are null in the history listing where per-event counts are not computed.
 */
public record RegistrationResponse(
    long registrationId,
    long eventId,
    RegistrationStatus status,
    String ticketNo,
    LocalDateTime registeredAt,
    Integer registeredCount,
    Integer availableSlots
) {}
