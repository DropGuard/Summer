package summer.aop;

/**
 * Represents a method interceptor that can wrap and modify method calls.
 */
public interface MethodInterceptor {
	/**
	 * Determines if this interceptor should be applied to the given target class.
	 */
	default boolean supports(Class<?> targetClass) {
		return true;
	}

	Object intercept(InvocationContext context) throws Throwable;
}