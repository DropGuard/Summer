package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/**
 * 409 – Email already taken during registration/update.
 */
public class DuplicateEmailException extends HttpException {
    public DuplicateEmailException(String message) {
        super(HttpStatus.CONFLICT.code(), message);
    }
}
