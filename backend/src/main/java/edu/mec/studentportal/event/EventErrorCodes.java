package edu.mec.studentportal.event;

/** Contract-approved stable error codes for the event module (docs/API_CONTRACT.md). */
final class EventErrorCodes {
    static final String EVENT_NOT_FOUND = "EVENT_NOT_FOUND";
    static final String EVENT_NOT_EDITABLE = "EVENT_NOT_EDITABLE";
    static final String INVALID_EVENT_STATE = "INVALID_EVENT_STATE";
    static final String VENUE_CONFLICT = "VENUE_CONFLICT";
    static final String CAPACITY_BELOW_CONFIRMED = "CAPACITY_BELOW_CONFIRMED";

    private EventErrorCodes() {}
}
