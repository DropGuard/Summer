package summer.test;

import summer.core.BeanContainer;
import summer.core.DiEngine;
import summer.core.Engine;
import summer.runtime.RuntimeBeanContainerBuilder;

/**
 * Unified test container builder for both AOT and Runtime engines.
 *
 * <pre>{@code
 * BeanContainer ctx = TestContainerBuilder.build(); // full scan
 * BeanContainer ctx = TestContainerBuilder.build(A.class, B.class); // isolated
 * BeanContainer ctx = TestContainerBuilder.buildAot(TestClass.class); // AOT full
 * BeanContainer ctx = TestContainerBuilder.buildAot(TestClass.class, A.class); // AOT isolated
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
	public static BeanContainer build(Class<?>... seeds) {
		if (seeds.length > 0) {
			return RuntimeBeanContainerBuilder.buildFromSeeds(seeds);
		}
		return RuntimeBeanContainerBuilder.build();
	}

	/**
	 * Builds a {@link BeanContainer} for the AOT engine. Loads a pre-generated
	 * {@code LocalContext_<testClassName>} if seeds are provided; otherwise uses
	 * the full AOT context.
	 *
	 * @param testClass
	 *            the test class (used to locate the generated LocalContext)
	 * @param seeds
	 *            optional entry bean classes
	 */
	public static BeanContainer buildAot(Class<?> testClass, Class<?>... seeds) {
		if (seeds.length > 0) {
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
