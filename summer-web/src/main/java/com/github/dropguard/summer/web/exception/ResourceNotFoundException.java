package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when a requested resource is not found. */
public class ResourceNotFoundException extends HttpException {
    public ResourceNotFoundException(String message) {

        super(HttpStatus.NOT_FOUND.code(), message);
    }

    /** Convenience method */
    public static ResourceNotFoundException notFound(String what) {
        return new ResourceNotFoundException(what + " not found");
    }
}
