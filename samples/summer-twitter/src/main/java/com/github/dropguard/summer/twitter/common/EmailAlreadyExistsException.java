package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.exception.ConflictException;

/** Thrown when an email already exists during registration. */
public class EmailAlreadyExistsException extends ConflictException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
