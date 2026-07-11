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
		if (isDevMode()) {
			log.info("💻 Dev mode detected (file protocol): using RUNTIME engine.");
			return Engine.RUNTIME;
		}
		log.info("🚀 Production mode detected (jar protocol): using AOT engine.");
		return Engine.AOT;
	}

	/**
	 * Creates a {@link BeanContainer} for the given engine mode.
	 */
	public static BeanContainer create(Engine engine, Object... externalBeans) {
		if (engine == Engine.AOT) {
			return createAot(externalBeans);
		}
		return invokeBuild(RUNTIME_CLASS, ErrorCode.CONFIG_RUNTIME_NOT_ON_CLASSPATH, externalBeans);
	}

	private static BeanContainer createAot(Object... externalBeans) {
		// Production mode: require AOT context, fail fast if missing
		try {
			Class<?> aotClass = Class.forName(AOT_CLASS);
			return (BeanContainer) aotClass.getMethod("build", Object[].class)
					.invoke(null, (Object) externalBeans);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(
					"\n=======================================================\n" +
					"❌ [Summer AOT] Fatal: GeneratedAotContext not found!\n\n" +
					"You explicitly requested Engine.AOT, but the AOT wiring code has not been generated.\n\n" +
					"👉 Solution: Please run 'mvn clean compile' first, OR start the application \n" +
					"using SummerApplication.run(args) to enable auto-detection.\n" +
					"=======================================================\n", e);
		} catch (ReflectiveOperationException e) {
			throw new ConfigurationException(ErrorCode.CONFIG_AOT_CONTEXT_NOT_FOUND, e.getMessage(), e);
		}
	}

	private static boolean isDevMode() {
		// 1. Explicit system property override
		String prop = System.getProperty("summer.dev-mode");
		if (prop != null) {
			return Boolean.parseBoolean(prop);
		}

		// 2. Debugger attach detection (IDE Run/Debug)
		if (java.lang.management.ManagementFactory.getRuntimeMXBean()
				.getInputArguments().stream()
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
		if (System.getenv("IDEA_INITIAL_DIRECTORY") != null || 
		    System.getProperty("idea.test.cyclic.buffer.size") != null) {
			return true;
		}
		return false;
	}

	private static BeanContainer invokeBuild(String className, ErrorCode errorCode, Object... externalBeans) {
		try {
			Class<?> clazz = Class.forName(className);
			return (BeanContainer) clazz.getMethod("build", Object[].class).invoke(null, (Object) externalBeans);
		} catch (ClassNotFoundException e) {
			throw new ConfigurationException(errorCode, className + " not found", e);
		} catch (ReflectiveOperationException e) {
			throw new ConfigurationException(errorCode, e.getMessage(), e);
		}
	}
}
