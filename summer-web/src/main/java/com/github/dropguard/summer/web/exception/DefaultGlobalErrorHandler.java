package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.web.ExceptionHandlerRegistrar;
import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.ExceptionHandler;
import java.util.List;

/**
 * Explicit error handler that maps each exception type to its own JSON response.
 *
 * <p>There is no "data: null" field — each exception type defines its own response shape.
 * Applications can provide their own registrar to override these mappings.
 */
// @Component
public class DefaultGlobalErrorHandler implements ExceptionHandlerRegistrar {

    private static final HttpStatus intToHttpStatus(int code) {
        switch (code) {
            case 200:
                return HttpStatus.OK;
            case 201:
                return HttpStatus.CREATED;
            case 204:
                return HttpStatus.NO_CONTENT;
            case 400:
                return HttpStatus.BAD_REQUEST;
            case 401:
                return HttpStatus.UNAUTHORIZED;
            case 403:
                return HttpStatus.FORBIDDEN;
            case 404:
                return HttpStatus.NOT_FOUND;
            case 405:
                return HttpStatus.METHOD_NOT_ALLOWED;
            case 409:
                return HttpStatus.CONFLICT;
            case 422:
                return HttpStatus.UNPROCESSABLE_ENTITY;
            case 500:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    @ExceptionHandler(AuthException.class)
    public void handleAuth(HttpContext ctx, AuthException ex) {
        ctx.json(
                intToHttpStatus(ex.getStatus()),
                new AuthErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public void handleNotFound(HttpContext ctx, NotFoundException ex) {
        ctx.json(
                intToHttpStatus(ex.getStatus()),
                new NotFoundErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public void handleValidation(HttpContext ctx, ValidationException ex) {
        ctx.json(
                intToHttpStatus(ex.getStatus()),
                new ValidationErrorResponse(ex.getStatus(), ex.getMessage(), ex.errors()));
    }

    @ExceptionHandler(ConflictException.class)
    public void handleConflict(HttpContext ctx, ConflictException ex) {
        ctx.json(
                intToHttpStatus(ex.getStatus()),
                new ConflictErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public void handleUnauthorized(HttpContext ctx, UnauthorizedException ex) {
        ctx.json(
                intToHttpStatus(ex.getStatus()),
                new UnauthorizedErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public void handleForbidden(HttpContext ctx, ForbiddenException ex) {
        ctx.json(
                intToHttpStatus(ex.getStatus()),
                new ForbiddenErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(InternalException.class)
    public void handleInternal(HttpContext ctx, InternalException ex) {
        ctx.json(
                intToHttpStatus(ex.getStatus()),
                new InternalErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(HttpException.class)
    public void handleHttp(HttpContext ctx, HttpException ex) {
        ctx.json(
                intToHttpStatus(ex.getStatus()),
                new HttpErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(Throwable.class)
    public void handleThrowable(HttpContext ctx, Throwable ex) {
        ctx.json(intToHttpStatus(500), new HttpErrorResponse(500, "Unexpected error"));
    }

    @Override
    public void registerHandlers(ExceptionRegistry registry, BeanContainer container) {
        // Handlers are registered via @ExceptionHandler annotations above;
        // the framework discovers them automatically.
    }
}

/** Auth error response: code + message only. */
record AuthErrorResponse(int code, String message) {}

/** Not found error response: code + message only. */
record NotFoundErrorResponse(int code, String message) {}

/** Validation error response: code + message + field errors. */
record ValidationErrorResponse(int code, String message, List<String> errors) {}

/** Conflict error response: code + message only. */
record ConflictErrorResponse(int code, String message) {}

/** Unauthorized error response: code + message only. */
record UnauthorizedErrorResponse(int code, String message) {}

/** Forbidden error response: code + message only. */
record ForbiddenErrorResponse(int code, String message) {}

/** Internal error response: code + message only. */
record InternalErrorResponse(int code, String message) {}

/** Generic HTTP error response: code + message only. */
record HttpErrorResponse(int code, String message) {}
