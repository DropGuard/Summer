package com.github.dropguard.summer.web.exception;

/**
 * Thrown when two {@code @ExceptionHandler} methods declare the same exception type. Only one
 * handler per exception type is allowed.
 */
public class ExceptionHandlerConflictException extends RuntimeException {

    public ExceptionHandlerConflictException(String message) {
        super(message);
    }
}
