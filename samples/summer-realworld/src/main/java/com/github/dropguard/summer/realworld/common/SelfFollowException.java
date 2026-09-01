package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/** 400 – Cannot follow oneself. */
public class SelfFollowException extends HttpException {
    public SelfFollowException(String message) {
        super(HttpStatus.BAD_REQUEST.code(), message);
    }
}
