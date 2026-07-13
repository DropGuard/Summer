package summer.runtime;

import java.lang.reflect.Method;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import summer.core.BeanContainer;
import summer.web.ExceptionHandlerRegistrar;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.annotation.ExceptionHandler;

/**
 * Reflection-based exception handler registrar that discovers
 * {@code @ExceptionHandler} methods at runtime via Jandex.
 */
public class RuntimeExceptionHandlerRegistrar implements ExceptionHandlerRegistrar {

	private final HttpParameterResolverChain resolverChain;
	private final IndexView index;

	public RuntimeExceptionHandlerRegistrar(HttpParameterResolverChain resolverChain, IndexView index) {
		this.resolverChain = resolverChain;
		this.index = index;
	}

	@Override
	public void registerHandlers(ExceptionRegistry registry, BeanContainer context) {
		for (ClassInfo ci : index.getKnownClasses()) {
			try {
				Class<?> clazz = Class.forName(ci.name().toString());
				if (clazz.isInterface() || !context.componentTypes().contains(clazz)) {
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
			} catch (ClassNotFoundException ignored) {
			}
		}
	}
}
