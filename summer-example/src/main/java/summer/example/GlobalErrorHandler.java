package summer.example;

import summer.core.Component;
import summer.web.HttpStatus;
import summer.web.WebContext;
import summer.web.annotation.ExceptionHandler;

@Component
public class GlobalErrorHandler {

	@ExceptionHandler(UserNotFoundException.class)
	public void handleUserNotFound(WebContext ctx, UserNotFoundException e) {
		ctx.json(HttpStatus.NOT_FOUND, new ErrorResponse("Resource Not Found", e.getMessage()));
	}

	@ExceptionHandler(RuntimeException.class)
	public void handleRuntimeException(WebContext ctx, RuntimeException e) {
		ctx.json(HttpStatus.BAD_REQUEST, new ErrorResponse("Bad Request", e.getMessage()));
	}

	public record ErrorResponse(String error, String message) {
	}
}
