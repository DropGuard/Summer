package summer.aop;

/**
 * Holds invocation context data and executes the interceptor chain.
 */
public interface InterceptorChain {

	Object getTarget();

	MethodMetadata getMethod();

	Object[] getArguments();

	Object proceed() throws Throwable;
}
