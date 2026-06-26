package summer.core;

import summer.core.exception.ConfigurationException;

/**
 * Abstraction for DI engine startup. Each engine (Runtime, AOT) provides an
 * implementation.
 *
 * <p>
 * Use {@link #resolve(Engine)} to obtain the engine for a given mode. This
 * eliminates duplicated if-else dispatch in {@code SummerApplication} and
 * {@code TestContainerBuilder}.
 * </p>
 *
 * <p>
 * AOT loading uses {@code Class.forName} because the generated class does not
 * exist at compile time — this is the only reflective path in the framework
 * outside {@code summer-runtime}, and is architecturally necessary.
 * </p>
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
