package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.exception.ConflictException;

/** Thrown when a username already exists during registration. */
public class UsernameAlreadyExistsException extends ConflictException {
    public UsernameAlreadyExistsException(String message) {
        super(message);
    }
}
