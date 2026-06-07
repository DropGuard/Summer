package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import summer.aop.InterceptorBinding;
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
			Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());

			if (!shouldIntercept(targetMethod, interceptors)) {
				return method.invoke(target, args);
			}

			try {
				return new ProxyInterceptorChain(target, new RuntimeMethodMetadata(targetMethod), args, interceptors,
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
				Class<?> clazz = interceptor.getClass();
				while (clazz != null && clazz != Object.class) {
					for (Annotation ann : clazz.getAnnotations()) {
						if (ann.annotationType().isAnnotationPresent(InterceptorBinding.class)
								&& targetMethod.isAnnotationPresent(ann.annotationType())) {
							return true;
						}
					}
					clazz = clazz.getSuperclass();
				}
			}
			return false;
		}
	}
}
