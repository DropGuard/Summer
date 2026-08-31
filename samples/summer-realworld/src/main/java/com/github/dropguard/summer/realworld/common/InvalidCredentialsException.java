package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/**
 * 401 – 邮箱/密码不匹配、token 缺失/过期/无效。
 */
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
