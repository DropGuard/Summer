package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/** 404 – Article not found. */
public class ArticleNotFoundException extends HttpException {
    public ArticleNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND.code(), message);
    }
}
