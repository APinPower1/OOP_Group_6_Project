package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Registration response per API_CONTRACT.md; counts are null in history listings. */
public record RegistrationDto(
    long registrationId,
    long eventId,
    RegistrationStatus status,
    String ticketNo,
    LocalDateTime registeredAt,
    Integer registeredCount,
    Integer availableSlots
) {}
