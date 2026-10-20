package edu.mec.studentportal.api;

/** Stable codes for errors shared across API modules. Domain modules may add contract-approved codes. */
public final class ApiErrorCodes {
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ApiErrorCodes() {}
}