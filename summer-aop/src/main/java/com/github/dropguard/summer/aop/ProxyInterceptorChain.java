package com.github.dropguard.summer.aop;

import java.util.List;

/**
 * Default implementation of {@link InterceptorChain}. Holds invocation data and
 * executes the interceptor chain.
 */
public class ProxyInterceptorChain implements InterceptorChain {

	private final Object target;
	private final InterceptedMethod targetMethod;
	private final Object[] args;
	private final List<MethodInterceptor> interceptors;
	private final TargetInvoker targetInvoker;
	private int currentIndex = -1;

	public ProxyInterceptorChain(Object target, InterceptedMethod targetMethod, Object[] args,
			List<MethodInterceptor> interceptors, TargetInvoker targetInvoker) {
		this.target = target;
		this.targetMethod = targetMethod;
		this.args = args;
		this.interceptors = interceptors;
		this.targetInvoker = targetInvoker;
	}

	@Override
	public Object getTarget() {
		return target;
	}

	@Override
	public InterceptedMethod method() {
		return targetMethod;
	}

	@Override
	public Object[] getArguments() {
		return args;
	}

	@Override
	public Object proceed() throws Throwable {
		currentIndex++;
		if (currentIndex < interceptors.size()) {
			MethodInterceptor interceptor = interceptors.get(currentIndex);
			return interceptor.intercept(this);
		} else {
			return targetInvoker.invoke();
		}
	}
}
