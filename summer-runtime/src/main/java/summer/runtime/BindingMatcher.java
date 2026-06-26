package summer.runtime;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import summer.aop.InterceptorBinding;

/**
 * Discovers {@code @InterceptorBinding}-annotated annotations on an interceptor
 * class. Shared by {@link BeanDefinitionFactory} (class-level discovery) and
 * {@link ProxyFactory} (method-level filtering).
 */
final class BindingMatcher {

	private BindingMatcher() {
	}

	static Set<Class<? extends Annotation>> findBindings(Class<?> interceptorClass) {
		Set<Class<? extends Annotation>> bindings = new HashSet<>();
		for (Annotation ann : interceptorClass.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(InterceptorBinding.class)) {
				bindings.add(ann.annotationType());
			}
		}
		return bindings;
	}
}
