package summer.core;

import java.util.List;
import java.util.Set;
import summer.core.exception.BeansException;

/**
 * The core Summer application context interface that manages beans and their
 * dependencies. This is the main entry point for the DI container.
 */
public interface ApplicationContext {

	/**
	 * Gets a bean instance of the given type.
	 */
	<T> T getBean(Class<T> type);

	/**
	 * Gets all bean instances that are assignable to the given type.
	 */
	<T> List<T> getBeansOfType(Class<T> type);

	/**
	 * Gets all registered component classes.
	 */
	Set<Class<?>> getComponentClasses();

	/**
	 * Destroys the context and releases all managed resources. Any singleton that
	 * implements {@link AutoCloseable} will have its {@code close()} method invoked
	 * in reverse instantiation order. This is called automatically by
	 * {@code SummerApplication} via a JVM shutdown hook.
	 */
	void destroy();

	/**
	 * Loads the compile-time generated AOT context. The class
	 * {@code summer.core.aot.GeneratedAotContext} must have been produced by
	 * {@code summer-compiler} during annotation processing.
	 */
	static ApplicationContext aot() {
		try {
			Class<?> clazz = Class.forName("summer.core.aot.GeneratedAotContext");
			return (ApplicationContext) clazz.getConstructor().newInstance();
		} catch (ClassNotFoundException e) {
			throw new BeansException(ErrorCode.BEAN_CREATION_FAILED,
					"GeneratedAotContext not found. Ensure summer-compiler is on the annotation processor path.");
		} catch (Exception e) {
			throw new BeansException(ErrorCode.BEAN_CREATION_FAILED, "Failed to load AOT context", e);
		}
	}

}
