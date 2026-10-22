package edu.mec.studentportal.client.dto;

import java.util.List;

/** Event roster with server-calculated seat counts. */
public record RosterDto(
    long eventId,
    int capacity,
    int registeredCount,
    int availableSlots,
    List<RosterEntry> registrations
) {
    public RosterDto {
        registrations = registrations == null ? List.of() : List.copyOf(registrations);
    }
}
