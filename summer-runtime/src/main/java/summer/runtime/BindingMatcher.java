package summer.runtime;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import summer.aop.InterceptorBinding;

/**
 * Discovers {@code @InterceptorBinding}-annotated annotations on an interceptor
 * class.
 *
 * <p>
 * Used by {@link ProxyFactory} during proxy construction. The interceptor →
 * binding-annotations map is cached after the first lookup because interceptor
 * classes and their binding annotations are fixed for the lifetime of the JVM.
 * </p>
 *
 * <p>
 * <strong>Note:</strong> The interceptor-to-bean matching phase
 * ({@link BeanDefinitionFactory#populateInterceptors}) reads binding
 * annotations from {@link summer.core.bean.BeanDefinition#interceptorBindingAnnotations}
 * — pre-computed strings populated at discovery time — rather than calling this
 * class. This class remains only for the per-method interception check inside
 * the proxy's {@code InvocationHandler}.
 * </p>
 */
final class BindingMatcher {

	private BindingMatcher() {
	}

	private static final Map<Class<?>, Set<Class<? extends Annotation>>> cache = new HashMap<>();

	/**
	 * Returns the set of {@code @InterceptorBinding}-annotated annotations declared
	 * on the given interceptor class.
	 *
	 * <p>
	 * Results are cached: each interceptor class is scanned at most once.
	 * </p>
	 */
	static Set<Class<? extends Annotation>> findBindings(Class<?> interceptorClass) {
		Set<Class<? extends Annotation>> cached = cache.get(interceptorClass);
		if (cached != null) {
			return cached;
		}
		Set<Class<? extends Annotation>> bindings = new HashSet<>();
		for (Annotation ann : interceptorClass.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(InterceptorBinding.class)) {
				bindings.add(ann.annotationType());
			}
		}
		cached = Collections.unmodifiableSet(bindings);
		cache.put(interceptorClass, cached);
		return cached;
	}
}
