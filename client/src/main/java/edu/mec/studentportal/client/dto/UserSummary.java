package edu.mec.studentportal.client.dto;

/** Login/me user summary from the auth contract; no password material ever crosses the wire. */
public record UserSummary(long id, String fullName, String email, Role role) {}
