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

	// --- Global Instance Management ---

	class Holder {
		private static volatile ApplicationContext INSTANCE;
	}

	/**
	 * Sets the global singleton application context.
	 */
	static void init(ApplicationContext context) {
		Holder.INSTANCE = context;
	}

	/**
	 * Gets the global singleton application context. Throws an exception if it
	 * hasn't been initialized yet.
	 */
	static ApplicationContext getInstance() {
		if (Holder.INSTANCE == null) {
			throw new BeansException(ErrorCode.BEAN_CREATION_FAILED, "ApplicationContext has not been initialized yet");
		}
		return Holder.INSTANCE;
	}

	/**
	 * Loads the compile-time generated AOT context. The class
	 * {@code summer.core.aot.GeneratedAotContext} must have been produced by
	 * {@code summer-compiler} during annotation processing.
	 */
	static ApplicationContext aot() {
		try {
			Class<?> clazz = Class.forName("summer.core.aot.GeneratedAotContext");
			ApplicationContext ctx = (ApplicationContext) clazz.getConstructor().newInstance();
			ApplicationContext.init(ctx);
			return ctx;
		} catch (ClassNotFoundException e) {
			throw new BeansException(ErrorCode.BEAN_CREATION_FAILED,
					"GeneratedAotContext not found. Ensure summer-compiler is on the annotation processor path.");
		} catch (Exception e) {
			throw new BeansException(ErrorCode.BEAN_CREATION_FAILED, "Failed to load AOT context", e);
		}
	}

}
