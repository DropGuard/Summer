package summer.example;

import summer.core.Component;
import summer.web.WebContext;
import summer.web.annotation.ExceptionHandler;

@Component
public class GlobalErrorHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public void handleUserNotFound(WebContext ctx, UserNotFoundException e) {
        ctx.response().setStatusCode(404);
        ctx.ok(new ErrorResponse("Resource Not Found", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public void handleRuntimeException(WebContext ctx, RuntimeException e) {
        ctx.response().setStatusCode(400);
        ctx.ok(new ErrorResponse("Bad Request", e.getMessage()));
    }

    public record ErrorResponse(String error, String message) {}
}
