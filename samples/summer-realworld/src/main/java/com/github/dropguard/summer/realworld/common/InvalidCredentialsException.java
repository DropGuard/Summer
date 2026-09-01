package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/** 401 – Email/password mismatch, or token missing/expired/invalid. */
public class InvalidCredentialsException extends HttpException {
    private final String field;

    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED.code(), message);
        this.field = null;
    }

    public InvalidCredentialsException(String field, String message) {
        super(HttpStatus.UNAUTHORIZED.code(), message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
