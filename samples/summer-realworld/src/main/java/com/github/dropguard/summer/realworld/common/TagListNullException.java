package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/** 422 – Explicitly passing null for tagList when updating an article. */
public class TagListNullException extends HttpException {
    public TagListNullException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY.code(), message);
    }
}
