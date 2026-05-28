package summer.tck.aop.dummy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import summer.aop.InvocationContext;
import summer.aop.MethodInterceptor;
import summer.core.Component;

/**
 * A test-purpose interceptor that: 1. Records every intercepted method call
 * into a log (for order verification). 2. Wraps the return value with
 * "[intercepted] " prefix (for result mutation verification).
 *
 * Only applies to GreeterService — verifies that supports() filtering works.
 */
@Component
public class RecordingInterceptor implements MethodInterceptor {

	/** Shared call log — tests can inspect this to verify interception happened. */
	private final List<String> callLog = new ArrayList<>();

	@Override
	public boolean supports(Class<?> targetClass) {
		// Only intercept GreeterService, not any other bean
		return GreeterService.class.isAssignableFrom(targetClass);
	}

	@Override
	public Object intercept(InvocationContext context) throws Throwable {
		String methodName = context.getMethod().getName();
		callLog.add("before:" + methodName);
		Object result = context.proceed();
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
