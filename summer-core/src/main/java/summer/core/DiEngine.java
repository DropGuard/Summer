package summer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.exception.ConfigurationException;

/**
 * DI engine bootstrap. Loads the engine implementation via
 * {@code Class.forName} and invokes its static {@code build()} method.
 *
 */
public final class DiEngine {

	private static final Logger log = LoggerFactory.getLogger(DiEngine.class);

	private static final String AOT_CLASS = "summer.core.aot.GeneratedAotContext";
	private static final String RUNTIME_CLASS = "summer.runtime.RuntimeBeanContainerBuilder";

	private DiEngine() {
	}

	public static Engine detectEngine() {
		// 1. Explicit override: -Dsummer.engine=runtime|aot|auto
		String override = System.getProperty("summer.engine", "").toLowerCase();
		if ("runtime".equals(override)) {
			log.info("[Summer] Engine override: RUNTIME");
			return Engine.RUNTIME;
		}
		if ("aot".equals(override)) {
			log.info("[Summer] Engine override: AOT");
			return Engine.AOT;
		}

		// 2. Auto-detection
		if (isDevMode()) {
			log.info("[Summer] Dev mode detected: using RUNTIME engine.");
			return Engine.RUNTIME;
		}
		log.info("[Summer] Production mode detected: using AOT engine.");
		return Engine.AOT;
	}

	/**
	 * Creates a {@link BeanContainer} by auto-detecting the environment.
	 */
	public static BeanContainer create(Object... externalBeans) {
		if (detectEngine() == Engine.AOT) {
			return invokeBuild(AOT_CLASS, ErrorCode.CONFIG_AOT_CONTEXT_NOT_FOUND,
					"AOT Context missing. Please ensure 'summer-maven-plugin' is configured for production builds.",
					externalBeans);
		}
		return invokeBuild(RUNTIME_CLASS, ErrorCode.CONFIG_RUNTIME_NOT_ON_CLASSPATH,
				"Runtime engine not found: " + RUNTIME_CLASS, externalBeans);
	}

	private static boolean isDevMode() {
		// 1. Debugger attach detection (IDE Run/Debug)
		if (java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
				.anyMatch(arg -> arg.startsWith("-agentlib:jdwp"))) {
			return true;
		}

		// 3. Stack frame location (Exploded directory = DEV, Fat JAR = PROD)
		try {
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			for (StackTraceElement element : stack) {
				try {
					Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(element.getClassName());
					if (clazz.getProtectionDomain() != null && clazz.getProtectionDomain().getCodeSource() != null) {
						java.net.URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
						if (location != null && "file".equals(location.getProtocol())) {
							// If loaded from a directory (not a .jar), we are in IDE / dev mode
							if (!location.getPath().endsWith(".jar")) {
								return true;
							}
						}
					}
				} catch (Throwable e) {
					// skip missing classes
				}
			}
		} catch (Throwable e) {
			// ignore
		}

		// 4. Fallback IDE environment detection
		if (System.getenv("IDEA_INITIAL_DIRECTORY") != null
				|| System.getProperty("idea.test.cyclic.buffer.size") != null) {
			return true;
		}
		return false;
	}

	private static BeanContainer invokeBuild(String className, ErrorCode notFoundCode, String notFoundMsg,
			Object... externalBeans) {
		try {
			Class<?> clazz = Class.forName(className);
			return (BeanContainer) clazz.getMethod("build", Object[].class).invoke(null, (Object) externalBeans);
		} catch (ClassNotFoundException e) {
			throw new ConfigurationException(notFoundCode, notFoundMsg, e);
		} catch (java.lang.reflect.InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException) {
				throw (RuntimeException) e.getCause();
			}
			throw new ConfigurationException(ErrorCode.INTERNAL_ERROR, "Engine initialization failed", e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new ConfigurationException(ErrorCode.INTERNAL_ERROR, "Failed to instantiate engine: " + className, e);
		}
	}
}
