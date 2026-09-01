package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/** 409 – Username already taken during registration/update. */
public class DuplicateUsernameException extends HttpException {
    public DuplicateUsernameException(String message) {
        super(HttpStatus.CONFLICT.code(), message);
    }
}
