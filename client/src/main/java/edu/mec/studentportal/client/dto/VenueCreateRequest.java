package edu.mec.studentportal.client.dto;

/** POST /admin/venues body — admin-managed venue name. */
public record VenueCreateRequest(String name) {}
