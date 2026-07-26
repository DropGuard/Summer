package com.github.dropguard.summer.issuetracker.common;

/**
 * Stable error envelope returned by {@link com.github.dropguard.summer.issuetracker.web.GlobalErrorHandler}.
 * Carries a machine-readable {@code code} and a human message; never leaks stack
 * traces or internal state.
 */
public record ErrorResponse(String code, String message) {
    public static ErrorResponse internalError() {
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
    }
}
