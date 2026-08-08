package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;

/**
 * Thrown when an operation conflicts with existing state (e.g. duplicate username). Maps to HTTP
 * 409.
 */
public class ConflictException extends BusinessException {

    public ConflictException(String field, String message) {
        super(HttpStatus.CONFLICT, "conflict", field, message);
    }
}
