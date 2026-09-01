package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/** 403 – Current user is not authorized to delete the comment. */
public class CommentForbiddenException extends HttpException {
    public CommentForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN.code(), message);
    }
}
