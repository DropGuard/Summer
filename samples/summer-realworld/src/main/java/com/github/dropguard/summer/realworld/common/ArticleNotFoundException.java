package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/**
 * 404 – 文章未找到
 */
public class ArticleNotFoundException extends HttpException {
    public ArticleNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND.code(), message);
    }
}
