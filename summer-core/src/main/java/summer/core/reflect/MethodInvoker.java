package summer.core.reflect;

/**
 * Invokes static methods reflectively. Implementations are provided by the
 * runtime or AOT engine; consuming modules never call
 * {@code java.lang.reflect} directly.
 */
public interface MethodInvoker {

	/**
	 * Invokes a static method on the target class.
	 *
	 * @param target
	 *            the class owning the method
	 * @param methodName
	 *            the method name
	 * @param paramTypes
	 *            the parameter types
	 * @param args
	 *            the arguments to pass
	 * @return the method's return value
	 */
	Object invokeStatic(Class<?> target, String methodName, Class<?>[] paramTypes, Object... args);
}
