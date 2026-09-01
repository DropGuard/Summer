package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/**
 * 403 – Current user is not authorized to edit/delete the article.
 */
public class ArticleForbiddenException extends HttpException {
    public ArticleForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN.code(), message);
    }
}
