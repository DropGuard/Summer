package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.SummerException;
import com.github.dropguard.summer.web.HttpStatus;

/**
 * Internal framework exception that carries an HTTP status code. Used for framework-infrastructure
 * failures (route resolution errors, encoding errors) that need to surface as HTTP responses.
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
