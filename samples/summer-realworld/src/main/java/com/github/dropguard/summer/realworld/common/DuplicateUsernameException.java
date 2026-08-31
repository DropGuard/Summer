package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/**
 * 409 – 注册/更新时用户名已被占用
 */
public class DuplicateUsernameException extends HttpException {
    public DuplicateUsernameException(String message) {
        super(HttpStatus.CONFLICT.code(), message);
    }
}
