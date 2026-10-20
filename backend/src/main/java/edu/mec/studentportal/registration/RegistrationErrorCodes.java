package edu.mec.studentportal.registration;

/** Contract-approved stable error codes for the registration module (docs/API_CONTRACT.md). */
final class RegistrationErrorCodes {
    static final String EVENT_NOT_FOUND = "EVENT_NOT_FOUND";
    static final String EVENT_NOT_APPROVED = "EVENT_NOT_APPROVED";
    static final String REGISTRATION_CLOSED = "REGISTRATION_CLOSED";
    static final String EVENT_FULL = "EVENT_FULL";
    static final String DUPLICATE_REGISTRATION = "DUPLICATE_REGISTRATION";
    static final String REGISTRATION_NOT_FOUND = "REGISTRATION_NOT_FOUND";
    static final String REGISTRATION_ALREADY_CANCELLED = "REGISTRATION_ALREADY_CANCELLED";

    private RegistrationErrorCodes() {}
}
