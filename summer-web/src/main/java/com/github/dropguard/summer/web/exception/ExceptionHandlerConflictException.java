package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.SummerException;

/**
 * Thrown when two {@code @ExceptionHandler} methods declare the same exception type. Only one
 * handler per exception type is allowed. A startup conflict: it belongs to the {@code
 * SummerException} assembly-failure tree, not to the request-scoped {@code HttpException} family.
 */
public class ExceptionHandlerConflictException extends SummerException {

    public ExceptionHandlerConflictException(String message) {
        super(ErrorCode.WEB_HANDLER_CONFLICT, message);
    }
}
