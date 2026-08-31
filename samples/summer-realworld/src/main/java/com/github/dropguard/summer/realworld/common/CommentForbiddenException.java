package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/**
 * 403 – 当前用户无权删除评论
 */
public class CommentForbiddenException extends HttpException {
    public CommentForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN.code(), message);
    }
}
