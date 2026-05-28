package summer.core;

/**
 * Abstraction for the DI engine that creates an {@link ApplicationContext}.
 * Implementations provide different bean discovery strategies (e.g.
 * compile-time AOT, runtime scanning).
 */
public interface DiEngine {
	/**
	 * Creates an application context rooted at the given entry point class. For
	 * runtime scanning, this determines the base package to scan. For AOT, the
	 * entry point may be ignored (compile-time discovery).
	 */
	ApplicationContext create(Class<?> entryPoint);
}
