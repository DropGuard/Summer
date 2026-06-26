package summer.test;

import summer.core.BeanContainer;
import summer.core.DiEngine;
import summer.core.Engine;
import summer.runtime.RuntimeBeanContainerBuilder;

/**
 * Unified test container builder for both AOT and Runtime engines.
 *
 * <pre>{@code
 * // Runtime — full classpath scan
 * TestContainerBuilder.buildRuntime();
 *
 * // Runtime — isolated from seed classes
 * TestContainerBuilder.buildRuntime(A.class, B.class);
 *
 * // AOT — loads pre-generated LocalContext for the test class
 * TestContainerBuilder.buildAot(TestClass.class);
 * }</pre>
 */
public final class TestContainerBuilder {

	private TestContainerBuilder() {
	}

	/**
	 * Builds a {@link BeanContainer} for the Runtime engine.
	 *
	 * <p>
	 * With no arguments, performs a full classpath scan. With seed classes, uses
	 * transitive dependency expansion (isolated).
	 * </p>
	 */
	public static BeanContainer buildRuntime(Class<?>... seeds) {
		if (seeds.length > 0) {
			return RuntimeBeanContainerBuilder.buildFromSeeds(seeds);
		}
		return RuntimeBeanContainerBuilder.build();
	}

	/**
	 * Builds a {@link BeanContainer} for the AOT engine. Loads the pre-generated
	 * {@code LocalContext_<testClassName>} for the given test class; falls back to
	 * the full AOT context if no test class is provided.
	 *
	 * @param testClass
	 *            the test class (used to locate the generated LocalContext), or
	 *            {@code null} for the full AOT context
	 */
	public static BeanContainer buildAot(Class<?> testClass) {
		if (testClass != null) {
			return loadAotLocalContext(testClass);
		}
		try {
			return DiEngine.create(Engine.AOT);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT context", e);
		}
	}

	private static BeanContainer loadAotLocalContext(Class<?> testClass) {
		String testClassName = testClass.getName().replace('.', '_').replace('$', '_');
		String aotClassName = "summer.core.aot.LocalContext_" + testClassName;
		try {
			Class<?> aotClass = Class.forName(aotClassName);
			return (BeanContainer) aotClass.getMethod("build").invoke(null);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("AOT LocalContext not found for " + testClass.getName()
					+ ". Ensure summer-maven-plugin is configured for the test phase.", e);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT LocalContext context for " + testClass.getName(), e);
		}
	}
}
