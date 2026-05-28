package summer.aop;

import java.util.List;

/**
 * Public implementation of InvocationContext supporting zero-reflection via
 * TargetInvoker and MethodMetadata.
 */
public class AotInvocationContext implements InvocationContext {

	private final Object target;
	private final MethodMetadata targetMethod;
	private final MethodMetadata interfaceMethod;
	private final Object[] args;
	private final List<MethodInterceptor> interceptors;
	private final TargetInvoker targetInvoker;
	private int currentIndex = -1;

	public AotInvocationContext(Object target, MethodMetadata targetMethod, MethodMetadata interfaceMethod,
			Object[] args, List<MethodInterceptor> interceptors, TargetInvoker targetInvoker) {
		this.target = target;
		this.targetMethod = targetMethod;
		this.interfaceMethod = interfaceMethod;
		this.args = args;
		this.interceptors = interceptors;
		this.targetInvoker = targetInvoker;
	}

	@Override
	public Object getTarget() {
		return target;
	}

	@Override
	public MethodMetadata getMethod() {
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
