package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when a referenced resource (tweet, etc.) does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "not_found", message);
    }
}
