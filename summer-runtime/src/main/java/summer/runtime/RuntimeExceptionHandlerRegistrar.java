package summer.runtime;

import java.lang.reflect.Method;
import summer.core.BeanContainer;
import summer.web.ExceptionHandlerRegistrar;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.annotation.ExceptionHandler;

/**
 * Exception handler registrar that discovers {@code @ExceptionHandler} methods
 * on registered beans.
 *
 * <p>
 * Reads from {@link BeanContainer#componentTypes()} — already populated by the
 * container construction phase — rather than re-scanning the Jandex index.
 * </p>
 */
public class RuntimeExceptionHandlerRegistrar implements ExceptionHandlerRegistrar {

	private final HttpParameterResolverChain resolverChain;

	public RuntimeExceptionHandlerRegistrar(HttpParameterResolverChain resolverChain) {
		this.resolverChain = resolverChain;
	}

	@Override
	public void registerHandlers(ExceptionRegistry registry, BeanContainer context) {
		for (Class<?> clazz : context.componentTypes()) {
			if (clazz.isInterface()) {
				continue;
			}
			Object instance = context.getBean(clazz);
			for (Method method : clazz.getMethods()) {
				ExceptionHandler ann = method.getAnnotation(ExceptionHandler.class);
				if (ann != null) {
					Handler handler = HandlerFactory.create(instance, method, resolverChain);
					registry.register(ann.value(), handler);
				}
			}
		}
	}
}
