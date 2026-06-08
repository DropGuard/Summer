package summer.runtime;

import java.lang.reflect.Method;
import summer.core.ApplicationContext;
import summer.web.ExceptionHandlerRegistrar;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.annotation.ExceptionHandler;

/**
 * Reflection-based exception handler registrar that discovers
 * {@code @ExceptionHandler} methods at runtime.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@link RuntimeWebConfiguration}. Only active when the runtime DI engine is in
 * use (i.e., {@code RuntimeDiMarker} is present).
 * </p>
 */
public class RuntimeExceptionHandlerRegistrar implements ExceptionHandlerRegistrar {

	private final HttpParameterResolverChain resolverChain;

	public RuntimeExceptionHandlerRegistrar(HttpParameterResolverChain resolverChain) {
		this.resolverChain = resolverChain;
	}

	@Override
	public void registerHandlers(ExceptionRegistry registry, ApplicationContext context) {
		for (Class<?> clazz : context.getRegisteredTypes()) {
			for (Method method : clazz.getMethods()) {
				ExceptionHandler ann = method.getAnnotation(ExceptionHandler.class);
				if (ann != null) {
					Handler handler = HandlerFactory.create(context.getBean(clazz), method, resolverChain);
					registry.register(ann.value(), handler);
				}
			}
		}
	}
}
