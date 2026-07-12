package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import summer.aop.MethodInterceptor;
import summer.aop.ProxyInterceptorChain;
import summer.aop.SummerAopException;
import summer.core.ErrorCode;

/**
 * Proxy factory that creates JDK dynamic proxies for interface-based AOP.
 */
public class ProxyFactory {

	private ProxyFactory() {
	}

	@SuppressWarnings("unchecked")
	public static <T> T createProxy(T target, List<MethodInterceptor> interceptors) {
		// Check if target implements any interfaces
		Class<?>[] interfaces = target.getClass().getInterfaces();
		if (interfaces.length == 0) {
			throw new SummerAopException(ErrorCode.AOP_ERROR, "Target object must implement at least one interface");
		}

		return (T) Proxy.newProxyInstance(target.getClass().getClassLoader(), interfaces,
				new ProxyInvocationHandler(target, interceptors));
	}

	private static class ProxyInvocationHandler implements InvocationHandler {
		private final Object target;
		private final List<MethodInterceptor> interceptors;
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

		ProxyInvocationHandler(Object target, List<MethodInterceptor> interceptors) {
			this.target = target;
			this.interceptors = interceptors;
			this.methodCache = new HashMap<>();

			// Pre-compile methods to avoid reflection lookup and annotation scanning on hot
			// path
			for (Class<?> iface : target.getClass().getInterfaces()) {
				for (Method method : iface.getMethods()) {
					try {
						Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
						boolean shouldIntercept = shouldIntercept(targetMethod, interceptors);
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
		 * Determines if a method should be intercepted by checking if any interceptor
		 * has an {@code @InterceptorBinding} annotation that matches an annotation on
		 * the target method.
		 */
		private boolean shouldIntercept(Method targetMethod, List<MethodInterceptor> interceptors) {
			for (MethodInterceptor interceptor : interceptors) {
				for (Class<? extends Annotation> binding : BindingMatcher.findBindings(interceptor.getClass())) {
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
	}
}
