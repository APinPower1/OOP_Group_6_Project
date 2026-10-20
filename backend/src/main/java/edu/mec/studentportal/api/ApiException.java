package edu.mec.studentportal.api;

import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

/** A deliberate API failure with a stable contract code and safe client-facing message. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        Assert.notNull(status, "status must not be null");
        Assert.isTrue(status.is4xxClientError(), "ApiException is for expected 4xx responses");
        Assert.hasText(code, "code must not be blank");
        Assert.hasText(message, "message must not be blank");
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}