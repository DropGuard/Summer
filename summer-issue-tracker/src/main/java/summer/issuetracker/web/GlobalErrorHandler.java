package summer.issuetracker.web;

import summer.core.Component;
import summer.issuetracker.common.BusinessException;
import summer.issuetracker.common.ErrorResponse;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.ExceptionHandler;

/**
 * Translates thrown exceptions to JSON error responses so controllers stay free
 * of hand-written try/catch. Business errors already carry their HTTP status.
 */
@Component
public class GlobalErrorHandler {

    @ExceptionHandler(BusinessException.class)
    public void handleBusiness(HttpContext ctx, BusinessException e) {
        ctx.json(e.status(), new ErrorResponse(e.code(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public void handleUnexpected(HttpContext ctx, Exception e) {
        e.printStackTrace(); // surface unexpected errors during development
        ctx.json(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.internalError());
    }
}
