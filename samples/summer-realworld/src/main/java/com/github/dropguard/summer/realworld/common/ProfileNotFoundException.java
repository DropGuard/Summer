package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/** 404 – Profile not found. */
public class ProfileNotFoundException extends HttpException {
    public ProfileNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND.code(), message);
    }
}
