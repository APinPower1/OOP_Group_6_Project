package edu.mec.studentportal.client.dto;

/** Managed venue id + name; feeds the event form's venue dropdown. */
public record VenueResponse(long id, String name) {}
