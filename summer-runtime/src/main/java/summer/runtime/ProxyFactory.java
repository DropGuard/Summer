package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import summer.aop.MethodInterceptor;
import summer.aop.ProxyInterceptorChain;
import summer.aop.SummerAopException;
import summer.core.ErrorCode;

/**
 * Proxy factory that creates JDK dynamic proxies for interface-based AOP.
 *
 * <p>
 * Interceptor binding annotations are read from a pre-computed map (populated
 * from {@link BeanDefinition#interceptorBindingAnnotations}) at proxy creation
 * time — no annotation scanning via reflection on the hot path.
 * </p>
 */
public class ProxyFactory {

	private ProxyFactory() {
	}

	@SuppressWarnings("unchecked")
	public static <T> T createProxy(T target, List<MethodInterceptor> interceptors,
			Map<Class<?>, Set<Class<? extends Annotation>>> interceptorBindings) {
		// Check if target implements any interfaces
		Class<?>[] interfaces = target.getClass().getInterfaces();
		if (interfaces.length == 0) {
			throw new SummerAopException(ErrorCode.AOP_ERROR, "Target object must implement at least one interface");
		}

		return (T) Proxy.newProxyInstance(target.getClass().getClassLoader(), interfaces,
				new ProxyInvocationHandler(target, interceptors, interceptorBindings));
	}

	private static class ProxyInvocationHandler implements InvocationHandler {
		private final Object target;
		private final List<MethodInterceptor> interceptors;
		private final Map<Class<?>, Set<Class<? extends Annotation>>> interceptorBindings;
		private final Map<Method, MethodCache> methodCache;

		private static class MethodCache {
			final Method targetMethod;
			final boolean shouldIntercept;
			final RuntimeMethodMetadata metadata;

			MethodCache(Method targetMethod, boolean shouldIntercept) {
				this.targetMethod = targetMethod;
				this.shouldIntercept = shouldIntercept;
				this.metadata = shouldIntercept ? new RuntimeMethodMetadata(targetMethod) : null;
			}
		}

		ProxyInvocationHandler(Object target, List<MethodInterceptor> interceptors,
				Map<Class<?>, Set<Class<? extends Annotation>>> interceptorBindings) {
			this.target = target;
			this.interceptors = interceptors;
			this.interceptorBindings = interceptorBindings;
			this.methodCache = new HashMap<>();

			// Pre-compile methods to avoid reflection lookup and annotation scanning on hot
			// path
			for (Class<?> iface : target.getClass().getInterfaces()) {
				for (Method method : iface.getMethods()) {
					try {
						Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
						boolean shouldIntercept = shouldIntercept(targetMethod);
						methodCache.put(method, new MethodCache(targetMethod, shouldIntercept));
					} catch (NoSuchMethodException e) {
						// Ignore
					}
				}
			}
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Handle Object methods specially (equals, hashCode, toString)
			if (method.getDeclaringClass() == Object.class) {
				return method.invoke(target, args);
			}

			MethodCache cache = methodCache.get(method);
			if (cache == null || !cache.shouldIntercept) {
				return method.invoke(target, args);
			}

			try {
				return new ProxyInterceptorChain(target, cache.metadata, args, interceptors,
						() -> method.invoke(target, args)).proceed();
			} catch (java.lang.reflect.InvocationTargetException e) {
				throw e.getCause();
			}
		}

		/**
		 * Determines if a method should be intercepted by checking if any interceptor's
		 * binding annotations match the target method.
		 *
		 * <p>
		 * Binding annotations are read from the pre-computed
		 * {@link #interceptorBindings} map. If an interceptor is not present in the map
		 * (e.g., created directly in tests), annotations are scanned as a fallback.
		 * </p>
		 */
		private boolean shouldIntercept(Method targetMethod) {
			for (MethodInterceptor interceptor : interceptors) {
				Set<Class<? extends Annotation>> bindings = interceptorBindings.get(interceptor.getClass());
				if (bindings == null) {
					bindings = scanBindings(interceptor.getClass());
				}
				for (Class<? extends Annotation> binding : bindings) {
					if (targetMethod.getDeclaringClass().isAnnotationPresent(binding)) {
						return true;
					}
					if (targetMethod.isAnnotationPresent(binding)) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Fallback: scans an interceptor class's annotations for
		 * {@code @InterceptorBinding} meta-annotations. Used when the pre-computed
		 * map does not contain the interceptor (e.g., unit tests creating interceptors
		 * directly).
		 */
		private static Set<Class<? extends Annotation>> scanBindings(Class<?> interceptorClass) {
			Set<Class<? extends Annotation>> bindings = new HashSet<>();
			for (java.lang.annotation.Annotation ann : interceptorClass.getAnnotations()) {
				if (ann.annotationType().isAnnotationPresent(summer.aop.InterceptorBinding.class)) {
					bindings.add(ann.annotationType());
				}
			}
			return Collections.unmodifiableSet(bindings);
		}
	}
}
