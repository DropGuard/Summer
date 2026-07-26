package com.github.dropguard.summer.fixtures.aop;

import com.github.dropguard.summer.aop.Interceptor;
import com.github.dropguard.summer.aop.InterceptorChain;
import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.core.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A test-purpose interceptor that: 1. Records every intercepted method call
 * into a log (for order verification). 2. Wraps the return value with
 * "[intercepted] " prefix (for result mutation verification).
 *
 * Bound to methods annotated with {@code @Logged}.
 */
@Component
@Interceptor
@Logged
public class RecordingInterceptor implements MethodInterceptor {

	/** Shared call log --tests can inspect this to verify interception happened. */
	private final List<String> callLog = new ArrayList<>();

	@Override
	public Object intercept(InterceptorChain chain) throws Throwable {
		String methodName = chain.method().name();
		callLog.add("before:" + methodName);
		Object result = chain.proceed();
		callLog.add("after:" + methodName);
		// Prefix the return value to prove interception ran
		return "[intercepted] " + result;
	}

	public List<String> getCallLog() {
		return Collections.unmodifiableList(callLog);
	}

	public void clearLog() {
		callLog.clear();
	}
}
