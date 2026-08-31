package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/**
 * 422 – 更新文章时显式传入 null 的 tagList
 */
public class TagListNullException extends HttpException {
    public TagListNullException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY.code(), message);
    }
}
