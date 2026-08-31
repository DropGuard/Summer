package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/**
 * 403 – 当前用户无权修改/删除文章
 */
public class ArticleForbiddenException extends HttpException {
    public ArticleForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN.code(), message);
    }
}
