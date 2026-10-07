package com.truthscan.exception;

import org.springframework.http.HttpStatus;

/** An error with an HTTP status and a code the frontend can react to. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException pendingReview(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "PENDING_REVIEW", message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "ALREADY_EXISTS", message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
