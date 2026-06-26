package summer.core;

import summer.core.exception.ConfigurationException;

/**
 * DI engine bootstrap. Resolves the engine implementation and creates a
 * {@link BeanContainer}.
 *
 * <pre>{@code
 * BeanContainer ctx = DiEngine.create(Engine.AOT, args);
 * BeanContainer ctx = DiEngine.create(Engine.RUNTIME, args);
 * }</pre>
 */
public final class DiEngine {

	private DiEngine() {
	}

	/**
	 * Creates a {@link BeanContainer} for the given engine mode.
	 *
	 * @param engine
	 *            the engine mode
	 * @return the fully initialized bean container
	 * @throws ConfigurationException
	 *             if the engine cannot be loaded
	 */
	public static BeanContainer create(Engine engine) {
		if (engine == Engine.AOT) {
			return invokeBuild("summer.core.aot.GeneratedAotContext", ErrorCode.CONFIG_AOT_CONTEXT_NOT_FOUND);
		}
		return invokeBuild("summer.runtime.RuntimeBeanContainerBuilder", ErrorCode.CONFIG_RUNTIME_NOT_ON_CLASSPATH);
	}

	private static BeanContainer invokeBuild(String className, ErrorCode errorCode) {
		try {
			Class<?> clazz = Class.forName(className);
			return (BeanContainer) clazz.getMethod("build").invoke(null);
		} catch (ClassNotFoundException e) {
			throw new ConfigurationException(errorCode, className + " not found", e);
		} catch (ReflectiveOperationException e) {
			throw new ConfigurationException(errorCode, e.getMessage(), e);
		}
	}
}
