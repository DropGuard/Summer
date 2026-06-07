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
		if (clazz.getInterfaces().length == 0 || instance instanceof MethodInterceptor) {
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
				// Found a binding annotation on the interceptor
				// Check if any method on the target class has the same annotation
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
