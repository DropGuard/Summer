package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.exception.NotFoundException;

/** Thrown when a user is not found. */
public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
