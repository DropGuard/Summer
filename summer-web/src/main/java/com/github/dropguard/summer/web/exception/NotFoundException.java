package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when a requested resource is not found. */
public class NotFoundException extends HttpException {
    public NotFoundException(String message) {

        super(HttpStatus.NOT_FOUND.code(), message);
    }

    /** Convenience method */
    public static NotFoundException notFound(String what) {
        return new NotFoundException(what + " not found");
    }
}
