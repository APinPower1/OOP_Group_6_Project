package edu.mec.studentportal.client.api;

/**
 * Standardized API failure: HTTP status plus the contract error body's stable code and
 * safe message. {@code status == 0} with code {@code NETWORK_ERROR} means the request
 * never reached the server (connection/DNS/timeout).
 */
public class ApiException extends RuntimeException {
    public static final int NETWORK_STATUS = 0;
    public static final String NETWORK_ERROR = "NETWORK_ERROR";

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public boolean isUnauthorized() {
        return status == 401;
    }

    public boolean isForbidden() {
        return status == 403;
    }

    public boolean isNetworkError() {
        return status == NETWORK_STATUS;
    }
}
