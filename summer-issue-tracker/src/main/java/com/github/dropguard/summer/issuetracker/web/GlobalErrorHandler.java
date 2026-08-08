package com.github.dropguard.summer.issuetracker.web;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.issuetracker.common.BusinessException;
import com.github.dropguard.summer.issuetracker.common.ErrorResponse;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.ExceptionHandler;
import com.github.dropguard.summer.web.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates thrown exceptions to JSON error responses so controllers stay free of hand-written
 * try/catch.
 */
@Component
public class GlobalErrorHandler {

    @ExceptionHandler(BusinessException.class)
    public void handleBusiness(HttpContext ctx, BusinessException e) {
        ctx.json(e.status(), new ErrorResponse(e.code(), e.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public void handleValidation(HttpContext ctx, ValidationException e) {
        List<Violation> violations = new ArrayList<>();
        int i = 0;
        for (String error : e.errors()) {
            violations.add(new Violation(String.valueOf(i++), error));
        }
        ctx.json(
                HttpStatus.BAD_REQUEST,
                new ViolationResponse("VALIDATION_ERROR", "Validation failed", violations));
    }

    @ExceptionHandler(Exception.class)
    public void handleUnexpected(HttpContext ctx, Exception e) {
        e.printStackTrace();
        ctx.json(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.internalError());
    }

    public record ViolationResponse(String code, String message, List<Violation> violations) {}

    public record Violation(String field, String message) {}
}
