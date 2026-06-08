package summer.runtime;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import summer.aop.InterceptorBinding;
import summer.aop.MethodInterceptor;
// ProxyFactory is now in the same package

final class RuntimeAopProcessor {

	private RuntimeAopProcessor() {
	}

	static Object applyProxy(Object instance, Class<?> clazz, List<MethodInterceptor> allInterceptors) {
		if (clazz.getInterfaces().length == 0
				|| instance.getClass().isAnnotationPresent(summer.aop.Interceptor.class)) {
			return instance;
		}

		List<MethodInterceptor> matching = allInterceptors.stream().filter(Objects::nonNull)
				.filter(interceptor -> hasMatchingBinding(interceptor, clazz)).toList();

		if (matching.isEmpty()) {
			return instance;
		}

		return ProxyFactory.createProxy(instance, matching);
	}

	/**
	 * Checks if an interceptor has an {@code @InterceptorBinding} annotation that
	 * matches any annotation on the target class's methods.
	 */
	private static boolean hasMatchingBinding(MethodInterceptor interceptor, Class<?> targetClass) {
		Class<?> interceptorClass = interceptor.getClass();
		for (Annotation ann : interceptorClass.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(InterceptorBinding.class)) {
				// Check class-level annotations on the target
				if (targetClass.isAnnotationPresent(ann.annotationType())) {
					return true;
				}
				// Check method-level annotations on the target
				for (java.lang.reflect.Method method : targetClass.getMethods()) {
					if (method.isAnnotationPresent(ann.annotationType())) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
