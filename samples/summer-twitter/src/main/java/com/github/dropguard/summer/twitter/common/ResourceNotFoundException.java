package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.exception.NotFoundException;

/** Thrown when a requested resource is not found. */
public class ResourceNotFoundException extends NotFoundException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
