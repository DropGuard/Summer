package summer.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Proxy factory that creates JDK dynamic proxies for interface-based AOP.
 */
public class ProxyFactory {
	@SuppressWarnings("unchecked")
	public static <T> T createProxy(T target, List<MethodInterceptor> interceptors) {
		// Check if target implements any interfaces
		Class<?>[] interfaces = target.getClass().getInterfaces();
		if (interfaces.length == 0) {
			throw new SummerAopException("Target object must implement at least one interface");
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
			// Handle Object methods specially
			if (method.getDeclaringClass() == Object.class) {
				return method.invoke(target, args);
			}

			return new IndexBasedInvocationContext(target, method, args, interceptors).proceed();
		}
	}

	private static class IndexBasedInvocationContext implements InvocationContext {
		private final Object target;
		private final Method method;
		private final Object[] args;
		private final List<MethodInterceptor> interceptors;
		private int currentIndex = -1;

		IndexBasedInvocationContext(Object target, Method method, Object[] args, List<MethodInterceptor> interceptors) {
			this.target = target;
			this.method = method;
			this.args = args;
			this.interceptors = interceptors;
		}

		@Override
		public Object getTarget() {
			return target;
		}

		@Override
		public Method getMethod() {
			return method;
		}

		@Override
		public Object[] getArguments() {
			return args;
		}

		@Override
		public Object proceed() throws Throwable {
			currentIndex++;
			if (currentIndex < interceptors.size()) {
				return interceptors.get(currentIndex).intercept(this);
			} else {
				return method.invoke(target, args);
			}
		}
	}

	private static class DefaultInvocationContext implements InvocationContext {
		private final Object target;
		private final Method method;
		private final Object[] args;

		DefaultInvocationContext(Object target, Method method, Object[] args) {
			this.target = target;
			this.method = method;
			this.args = args;
		}

		@Override
		public Object getTarget() {
			return target;
		}

		@Override
		public Method getMethod() {
			return method;
		}

		@Override
		public Object[] getArguments() {
			return args;
		}

		@Override
		public Object proceed() throws Throwable {
			return method.invoke(target, args);
		}
	}
}
