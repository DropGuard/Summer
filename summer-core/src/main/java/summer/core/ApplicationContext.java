package summer.core;

import java.util.List;
import java.util.Set;

/**
 * The core Summer application context interface that manages beans and their
 * dependencies. This is the main entry point for the DI container.
 */
public interface ApplicationContext extends AutoCloseable {

	/**
	 * Returns the DI engine type used by this context.
	 */
	Engine engine();

	/**
	 * Gets a bean instance of the given type.
	 */
	<T> T getBean(Class<T> type);

	/**
	 * Gets all bean instances that are assignable to the given type.
	 */
	<T> List<T> getBeans(Class<T> type);

	/**
	 * Returns all registered component types. This is primarily used by framework
	 * internals (e.g. route discovery) that need to inspect annotations on
	 * registered classes.
	 */
	Set<Class<?>> getRegisteredTypes();

	/**
	 * Closes the context and releases all managed resources. Any singleton that
	 * implements {@link AutoCloseable} will have its {@code close()} method invoked
	 * in reverse instantiation order.
	 */
	@Override
	void close() throws Exception;

}
