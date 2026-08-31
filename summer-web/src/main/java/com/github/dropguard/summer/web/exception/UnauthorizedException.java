package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when the user is not authenticated or authorized. */
public class UnauthorizedException extends HttpException {
    public UnauthorizedException(String message) {

        super(HttpStatus.UNAUTHORIZED.code(), message);
    }

    /** Convenience method */
    public static UnauthorizedException unauthorized(String message) {
        return new UnauthorizedException(message);
    }
}
