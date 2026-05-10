package summer.aop;

/**
 * Represents a method interceptor that can wrap and modify method calls.
 */
public interface MethodInterceptor {
	Object intercept(InvocationContext context) throws Throwable;
}