package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.HttpException;

/**
 * 429 – 登录/操作频率受限
 */
public class RateLimitedException extends HttpException {
    public RateLimitedException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS.code(), message);
    }
}
