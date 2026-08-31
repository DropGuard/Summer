package com.github.dropguard.summer.realworld;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.realworld.common.InvalidCredentialsException;
import com.github.dropguard.summer.realworld.user.UserDtos;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.ExceptionHandler;
import com.github.dropguard.summer.web.exception.HttpException;
import com.github.dropguard.summer.web.exception.ValidationException;

/**
 * Global exception handler for the realworld demo.
 *
 * <p>All HTTP-related errors extend {@link HttpException} and carry the proper status code.
 * {@link ValidationException} carries field-level errors and is rendered as
 * {@code {"errors":{body:[firstError]}}} to keep the RealWorld shape.
 * {@link InvalidCredentialsException} carries an optional field name; if present it is rendered
 * as {@code {"errors":{field:[message]}}} for a consistent authentication-error shape; otherwise
 * it falls back to a generic {@code "error"} key.
 * Other exceptions fall through to a generic 500 response.
 */
@Component
public class GlobalErrorHandler {

    private static final HttpStatus intToHttpStatus(int code) {
        switch (code) {
            case 200: return HttpStatus.OK;
            case 201: return HttpStatus.CREATED;
            case 204: return HttpStatus.NO_CONTENT;
            case 400: return HttpStatus.BAD_REQUEST;
            case 401: return HttpStatus.UNAUTHORIZED;
            case 403: return HttpStatus.FORBIDDEN;
            case 404: return HttpStatus.NOT_FOUND;
            case 409: return HttpStatus.CONFLICT;
            case 422: return HttpStatus.UNPROCESSABLE_ENTITY;
            case 429: return HttpStatus.TOO_MANY_REQUESTS;
            case 500: return HttpStatus.INTERNAL_SERVER_ERROR;
            default: return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    @ExceptionHandler(HttpException.class)
    public void handleHttp(HttpContext ctx, HttpException e) {
        if (e instanceof ValidationException) {
            ValidationException ve = (ValidationException) e;
            ctx.json(intToHttpStatus(ve.getStatus()),
                    UserDtos.ErrorResponse.of("body", ve.errors().get(0)));
        } else if (e instanceof InvalidCredentialsException) {
            InvalidCredentialsException ice = (InvalidCredentialsException) e;
            if (ice.field() != null) {
                ctx.json(intToHttpStatus(ice.getStatus()),
                        UserDtos.ErrorResponse.of(ice.field(), ice.getMessage()));
            } else {
                ctx.json(intToHttpStatus(ice.getStatus()),
                        UserDtos.ErrorResponse.of("error", ice.getMessage()));
            }
        } else {
            ctx.json(intToHttpStatus(e.getStatus()),
                    UserDtos.ErrorResponse.of("error", e.getMessage()));
        }
    }

    @ExceptionHandler(Exception.class)
    public void handleUnexpected(HttpContext ctx, Exception e) {
        ctx.json(
                HttpStatus.INTERNAL_SERVER_ERROR,
                UserDtos.ErrorResponse.of("error", "An unexpected error occurred"));
    }
}
