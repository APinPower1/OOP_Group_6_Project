package edu.mec.studentportal.event;

/** Persisted states of an event row; matches the events.status CHECK constraint. */
public enum EventStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED
}
