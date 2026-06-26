package summer.core;

import summer.core.exception.ConfigurationException;

/**
 * Abstraction for DI engine startup. Each engine (Runtime, AOT) provides an
 * implementation.
 *
 * <pre>{@code
 * BeanContainer ctx = DiEngine.resolve(Engine.AOT).create();
 * BeanContainer ctx = DiEngine.resolve(Engine.RUNTIME).create();
 * }</pre>
 */
public interface DiEngine {

	/**
	 * Creates and returns a fully initialized {@link BeanContainer}.
	 *
	 * @return the bean container
	 * @throws Exception
	 *             if initialization fails
	 */
	BeanContainer create() throws Exception;

	/**
	 * Resolves the {@link DiEngine} for the given engine mode.
	 *
	 * @param engine
	 *            the engine mode
	 * @return the engine implementation
	 * @throws ConfigurationException
	 *             if the engine cannot be loaded
	 */
	static DiEngine resolve(Engine engine) {
		if (engine == Engine.AOT) {
			return resolveEngine("summer.core.aot.GeneratedAotContext", ErrorCode.CONFIG_AOT_CONTEXT_NOT_FOUND);
		}
		return resolveEngine("summer.runtime.RuntimeBeanContainerBuilder", ErrorCode.CONFIG_RUNTIME_NOT_ON_CLASSPATH);
	}

	private static DiEngine resolveEngine(String className, ErrorCode errorCode) {
		try {
			Class<?> clazz = Class.forName(className);
			return (DiEngine) clazz.getConstructor().newInstance();
		} catch (ClassNotFoundException e) {
			throw new ConfigurationException(errorCode, className + " not found", e);
		} catch (ReflectiveOperationException e) {
			throw new ConfigurationException(errorCode, e.getMessage(), e);
		}
	}
}
