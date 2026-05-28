package summer.aop;

/**
 * Represents an interception context.
 */
public interface InvocationContext {

	Object getTarget();

	MethodMetadata getMethod();

	Object[] getArguments();

	Object proceed() throws Throwable;
}