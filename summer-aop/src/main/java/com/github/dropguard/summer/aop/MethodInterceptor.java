package com.github.dropguard.summer.aop;

/**
 * Represents a method interceptor that can wrap and modify method calls.
 *
 * <p>
 * Interceptors are bound to business methods via {@link InterceptorBinding}
 * annotations. The framework automatically applies interceptors to methods that
 * share the same binding annotation.
 * </p>
 *
 * @see InterceptorBinding
 */
public interface MethodInterceptor {

	Object intercept(InterceptorChain chain) throws Throwable;
}
