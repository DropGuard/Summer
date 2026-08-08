package com.github.dropguard.summer.twitter.common;

/**
 * Uniform error body returned by {@code GlobalErrorHandler}. Carries a stable machine-readable
 * {@code code} and a human-readable {@code message}; it never echoes raw exception internals beyond
 * the predefined message.
 */
public record ErrorResponse(String code, String message) {

    /** Fixed body for unexpected server errors (HTTP 500) — leaks nothing. */
    public static ErrorResponse internalError() {
        return new ErrorResponse("internal_error", "An unexpected error occurred");
    }
}
