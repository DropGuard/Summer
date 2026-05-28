package summer.web;

import java.util.HashMap;
import java.util.Map;
import summer.core.Component;

/**
 * Registry for global exception handlers.
 */
@Component
public class ExceptionRegistry {
	private final Map<Class<? extends Throwable>, Handler> handlers = new HashMap<>();

	public void register(Class<? extends Throwable> exceptionType, Handler handler) {
		handlers.put(exceptionType, handler);
	}

	public Handler getHandler(Throwable throwable) {
		Class<?> current = throwable.getClass();
		while (current != null && Throwable.class.isAssignableFrom(current)) {
			Handler handler = handlers.get(current);
			if (handler != null) {
				return handler;
			}
			current = current.getSuperclass();
		}
		return null;
	}
}
