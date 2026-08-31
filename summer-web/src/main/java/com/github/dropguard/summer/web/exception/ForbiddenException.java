package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when the user is authenticated but not authorized for the resource. */
public class ForbiddenException extends HttpException {
    public ForbiddenException(String message) {

        super(HttpStatus.FORBIDDEN.code(), message);
    }

    /** Convenience method */
    public static ForbiddenException forbidden(String message) {
        return new ForbiddenException(message);
    }
}
