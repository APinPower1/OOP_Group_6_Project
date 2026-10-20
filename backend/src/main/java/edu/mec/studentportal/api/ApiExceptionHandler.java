package edu.mec.studentportal.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps expected MVC failures to the shared JSON error contract without exposing internals. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
        ApiException exception,
        HttpServletRequest request
    ) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(HttpServletRequest request) {
        return error(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.INVALID_REQUEST,
            "The request is invalid or incomplete.",
            request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request) {
        return error(
            HttpStatus.NOT_FOUND,
            ApiErrorCodes.NOT_FOUND,
            "The requested resource was not found.",
            request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpServletRequest request) {
        return error(
            HttpStatus.METHOD_NOT_ALLOWED,
            ApiErrorCodes.METHOD_NOT_ALLOWED,
            "The request method is not supported for this resource.",
            request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
        return error(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            ApiErrorCodes.UNSUPPORTED_MEDIA_TYPE,
            "The request content type is not supported.",
            request
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
        ResponseStatusException exception,
        HttpServletRequest request
    ) {
        HttpStatusCode status = exception.getStatusCode();
        String code = codeFor(status.value());
        String message = status.is4xxClientError() && exception.getReason() != null
            ? exception.getReason()
            : defaultMessage(status.value());
        return ResponseEntity.status(status).body(
            ApiErrorResponse.of(status.value(), code, message, request.getRequestURI())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("Unhandled API error for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiErrorCodes.INTERNAL_ERROR,
            "An unexpected server error occurred.",
            request
        );
    }

    private ResponseEntity<ApiErrorResponse> error(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(
            ApiErrorResponse.of(status.value(), code, message, request.getRequestURI())
        );
    }

    private static String codeFor(int status) {
        return switch (status) {
            case 400 -> ApiErrorCodes.INVALID_REQUEST;
            case 401 -> ApiErrorCodes.UNAUTHENTICATED;
            case 403 -> ApiErrorCodes.FORBIDDEN;
            case 404 -> ApiErrorCodes.NOT_FOUND;
            case 405 -> ApiErrorCodes.METHOD_NOT_ALLOWED;
            case 409 -> ApiErrorCodes.CONFLICT;
            case 415 -> ApiErrorCodes.UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? ApiErrorCodes.INTERNAL_ERROR : "REQUEST_REJECTED";
        };
    }

    private static String defaultMessage(int status) {
        return status >= 500
            ? "An unexpected server error occurred."
            : "The request could not be completed.";
    }
}