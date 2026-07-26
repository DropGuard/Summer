package com.github.dropguard.summer.web;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for global exception handlers. Built during startup by
 * {@link ExceptionHandlerRegistrar} and used by the server to handle
 * exceptions.
 */
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
