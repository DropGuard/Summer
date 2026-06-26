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

	public RuntimeExceptionHandlerRegistrar(HttpParameterResolverChain resolverChain) {
		this.resolverChain = resolverChain;
	}

	@Override
	public void registerHandlers(ExceptionRegistry registry, BeanContainer context) {
		IndexView index = JandexIndexLoader.buildIndex();
		for (ClassInfo ci : index.getKnownClasses()) {
			try {
				Class<?> clazz = Class.forName(ci.name().toString());
				if (!context.componentTypes().contains(clazz)) {
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
