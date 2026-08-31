package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when the request is malformed or invalid (HTTP 400). */
public class BadRequestException extends HttpException {
    public BadRequestException(String message) {

        super(HttpStatus.BAD_REQUEST.code(), message);
    }

    public static BadRequestException badRequest(String message) {
        return new BadRequestException(message);
    }
}
