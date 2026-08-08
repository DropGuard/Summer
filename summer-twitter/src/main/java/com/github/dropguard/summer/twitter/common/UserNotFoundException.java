package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when a referenced user (by id or username) does not exist. Maps to HTTP 404. */
public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "user_not_found", message);
    }
}
