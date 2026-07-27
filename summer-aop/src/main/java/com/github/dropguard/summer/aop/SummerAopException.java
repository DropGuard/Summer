package com.github.dropguard.summer.aop;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.SummerException;

/** Exception class for AOP related errors in Summer framework. */
public class SummerAopException extends SummerException {
    public SummerAopException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SummerAopException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /** Backward-compatible constructor. */
    public SummerAopException(String message) {
        super(ErrorCode.AOP_ERROR, message);
    }

    /** Backward-compatible constructor. */
    public SummerAopException(String message, Throwable cause) {
        super(ErrorCode.AOP_ERROR, message, cause);
    }
}
