package edu.mec.studentportal.client.dto;

/** Event lifecycle states; matches the events.status CHECK constraint. */
public enum EventStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED
}
