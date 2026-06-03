package summer.aop;

/**
 * Functional interface used to execute the target method without reflection.
 * This allows the DefaultInvocationContext to be 100% reflection-free during
 * invocation.
 */
@FunctionalInterface
public interface TargetInvoker {
	/**
	 * Executes the target method.
	 *
	 * @return the result of the method execution
	 * @throws Throwable
	 *             if the target method throws any exception
	 */
	Object invoke() throws Throwable;
}
