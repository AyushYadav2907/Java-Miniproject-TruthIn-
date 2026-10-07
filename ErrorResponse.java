package com.truthscan.dto;

import java.util.List;

/**
 * JSON body for every error.
 *
 * @param code    machine-readable code the frontend checks, e.g. "NOT_FOUND", "PENDING_REVIEW"
 * @param message human-readable message
 * @param details field validation errors, if any
 */
public record ErrorResponse(String code, String message, List<String> details) {

    public ErrorResponse(String code, String message) {
        this(code, message, List.of());
    }
}
