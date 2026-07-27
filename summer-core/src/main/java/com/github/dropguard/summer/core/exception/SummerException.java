package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/** Base exception class for all Summer framework exceptions. */
public class SummerException extends RuntimeException {
    private final ErrorCode errorCode;

    public SummerException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public SummerException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SummerException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
