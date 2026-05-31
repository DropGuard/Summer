package summer.tck.web.dummy;

import summer.core.Component;
import summer.web.annotation.ExceptionHandler;

@Component
public class MyExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public String handleIllegalArgument(IllegalArgumentException ex) {
		return "error_caught:" + ex.getMessage();
	}
}
