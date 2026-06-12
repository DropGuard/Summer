package summer.fixtures.web.dummy;

import summer.core.Component;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.ExceptionHandler;

@Component
public class MyExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public void handleIllegalArgument(IllegalArgumentException ex, HttpContext ctx) {
		ctx.text(HttpStatus.BAD_REQUEST, "error_caught:" + ex.getMessage());
	}
}
