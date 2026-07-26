package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.aop.ProxyInterceptorChain;
import com.github.dropguard.summer.aop.SummerAopException;
import com.github.dropguard.summer.core.ErrorCode;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proxy factory that creates JDK dynamic proxies for interface-based AOP.
 *
 * <p>
 * The interception decision (which interface method is wrapped, and by which
 * interceptors) is computed at assembly time by {@link RuntimeAopProcessor}
 * from the bean's pre-enriched binding data ({@code BeanDefinition}), not by
 * reflection on the hot path. This factory is therefore a pure lookup table: it
 * receives a {@link ProxyMethodSpec} per interface method and, at dispatch,
 * either forwards straight to the target or runs the method's interceptor
 * chain. No annotation scanning, no impl-vs-interface ambiguity to resolve
 * here.
 * </p>
 */
public class ProxyFactory {

	private ProxyFactory() {
	}

	/**
	 * Per-method proxy plan: the interceptors to run for a method, plus the binding
	 * annotation types that method carries (exposed to interceptors via
	 * {@link com.github.dropguard.summer.aop.InterceptedMethod#isAnnotationPresent}).
	 * Computed once at assembly time, never on the hot path.
	 */
	public record ProxyMethodSpec(List<MethodInterceptor> interceptors, Set<Class<? extends Annotation>> bindings) {
		/** Methods with no interceptors are dispatched straight through. */
		static final ProxyMethodSpec NONE = new ProxyMethodSpec(List.of(), Set.of());
	}

	@SuppressWarnings("unchecked")
	public static <T> T createProxy(T target, Map<Method, ProxyMethodSpec> methodSpecs) {
		Class<?>[] interfaces = target.getClass().getInterfaces();
		if (interfaces.length == 0) {
			throw new SummerAopException(ErrorCode.AOP_NO_INTERFACE,
					"Target object must implement at least one interface");
		}

		return (T) Proxy.newProxyInstance(target.getClass().getClassLoader(), interfaces,
				new ProxyInvocationHandler(target, methodSpecs));
	}

	private static class ProxyInvocationHandler implements InvocationHandler {
		private final Object target;
		private final Map<Method, ProxyMethodSpec> methodSpecs;

		ProxyInvocationHandler(Object target, Map<Method, ProxyMethodSpec> methodSpecs) {
			this.target = target;
			this.methodSpecs = methodSpecs;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Handle Object methods specially (equals, hashCode, toString)
			if (method.getDeclaringClass() == Object.class) {
				return method.invoke(target, args);
			}

			ProxyMethodSpec spec = methodSpecs.getOrDefault(method, ProxyMethodSpec.NONE);
			if (spec.interceptors().isEmpty()) {
				return method.invoke(target, args);
			}

			try {
				var chain = new ProxyInterceptorChain(target,
						new com.github.dropguard.summer.aop.InterceptedMethod(method.getName(), spec.bindings()), args,
						spec.interceptors(), () -> method.invoke(target, args));
				return chain.proceed();
			} catch (java.lang.reflect.InvocationTargetException e) {
				throw e.getCause();
			}
		}
	}
}
