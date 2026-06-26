package summer.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.HttpStatus;

class ExceptionHandlerTest {

	@Test
	void registryResolvesByExceptionType() {
		ExceptionRegistry registry = new ExceptionRegistry();
		registry.register(IllegalArgumentException.class, ctx -> {
			ctx.text(HttpStatus.BAD_REQUEST, "bad request");
		});

		Handler handler = registry.getHandler(new IllegalArgumentException("test"));
		assertNotNull(handler);
	}

	@Test
	void registryReturnsNullForUnknown() {
		ExceptionRegistry registry = new ExceptionRegistry();
		Handler handler = registry.getHandler(new RuntimeException("unknown"));
		assertNull(handler);
	}
}
