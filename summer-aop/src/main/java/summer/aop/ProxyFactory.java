package summer.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import summer.core.ErrorCode;

/**
 * Proxy factory that creates JDK dynamic proxies for interface-based AOP.
 */
public class ProxyFactory {
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

		ProxyInvocationHandler(Object target, List<MethodInterceptor> interceptors) {
			this.target = target;
			this.interceptors = interceptors;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Handle Object methods specially (equals, hashCode, toString)
			if (method.getDeclaringClass() == Object.class) {
				return method.invoke(target, args);
			}

			// Find the corresponding method on the implementation class (target)
			Method targetMethod;
			try {
				targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
			} catch (NoSuchMethodException e) {
				targetMethod = method;
			}

			if (!shouldIntercept(targetMethod, interceptors)) {
				return method.invoke(target, args);
			}

			return new AotInvocationContext(target, new RuntimeMethodMetadata(targetMethod),
					new RuntimeMethodMetadata(method), args, interceptors, () -> {
						try {
							return method.invoke(target, args);
						} catch (java.lang.reflect.InvocationTargetException e) {
							throw e.getCause();
						}
					}).proceed();
		}

		private boolean shouldIntercept(Method targetMethod, List<MethodInterceptor> interceptors) {
			// 1. Explicitly annotated with @Intercepted
			if (targetMethod.isAnnotationPresent(Intercepted.class)) {
				return true;
			}
			// 2. Annotated with any trigger annotation declared by the active interceptors
			for (MethodInterceptor interceptor : interceptors) {
				Intercepts intercepts = interceptor.getClass().getAnnotation(Intercepts.class);
				if (intercepts != null) {
					for (Class<? extends java.lang.annotation.Annotation> ann : intercepts.annotations()) {
						if (targetMethod.isAnnotationPresent(ann)) {
							return true;
						}
					}
				}
			}
			return false;
		}
	}
}
