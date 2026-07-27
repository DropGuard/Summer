package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.SummerException;
import com.github.dropguard.summer.web.HttpStatus;

/**
 * Base class for all Web and HTTP-related exceptions in Summer. Carries an HTTP status code for
 * automatic response mapping.
 */
public class SummerWebException extends SummerException {
    private final HttpStatus status;

    public SummerWebException(ErrorCode errorCode, HttpStatus status, String message) {
        super(errorCode, message);
        this.status = status;
    }

    public SummerWebException(
            ErrorCode errorCode, HttpStatus status, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.status = status;
    }

    public HttpStatus statusCode() {
        return status;
    }
}
