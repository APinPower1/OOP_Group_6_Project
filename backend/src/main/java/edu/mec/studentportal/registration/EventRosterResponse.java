package edu.mec.studentportal.registration;

import java.util.List;

/** Event roster with seat counts calculated from CONFIRMED registration rows. */
public record EventRosterResponse(
    long eventId,
    int capacity,
    int registeredCount,
    int availableSlots,
    List<RosterEntryResponse> registrations
) {}
