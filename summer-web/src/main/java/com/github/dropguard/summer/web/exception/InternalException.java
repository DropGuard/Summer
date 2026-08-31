package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown for unexpected internal errors. */
public class InternalException extends HttpException {
    public InternalException(String message) {

        super(HttpStatus.INTERNAL_SERVER_ERROR.code(), message);
    }

    /** Convenience method */
    public static InternalException internalError(String message) {
        return new InternalException(message);
    }
}
