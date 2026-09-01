package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/** 404 – Comment not found. */
public class CommentNotFoundException extends HttpException {
    public CommentNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND.code(), message);
    }
}
