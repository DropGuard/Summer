package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when request body parsing fails. */
public class BodyParseException extends HttpException {
    public BodyParseException(String converterName, Throwable cause) {
        super(HttpStatus.BAD_REQUEST.code(), "Failed to parse body with " + converterName);
        // Note: cause is not stored as it's not needed for HTTP response;
        // logging should handle it elsewhere if needed
    }
}
