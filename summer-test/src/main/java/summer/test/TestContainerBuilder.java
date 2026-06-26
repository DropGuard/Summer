package summer.test;

import java.util.Map;
import summer.core.BeanContainer;
import summer.core.DiEngine;
import summer.core.Engine;
import summer.runtime.RuntimeBeanContainerBuilder;

/**
 * Unified test container builder for both AOT and Runtime engines.
 *
 * <p>
 * Encapsulates engine selection, entry bean isolation (LocalContext), and
 * config overrides into a single builder.
 * </p>
 *
 * <pre>{@code
 * // Full classpath scan
 * BeanContainer ctx = TestContainerBuilder.create().build();
 *
 * // Isolated with entry beans
 * BeanContainer ctx = TestContainerBuilder.create().withEntryBeans(CircularA.class, CircularB.class).build();
 *
 * // AOT engine
 * BeanContainer ctx = TestContainerBuilder.create().withEngine(Engine.AOT).withEntryBeans(MyService.class).build();
 * }</pre>
 */
public final class TestContainerBuilder {

	private Engine engine = Engine.RUNTIME;
	private Class<?>[] entryBeans;
	private Class<?> testClass;
	private Map<String, String> configOverrides;

	private TestContainerBuilder() {
	}

	public static TestContainerBuilder create() {
		return new TestContainerBuilder();
	}

	public TestContainerBuilder withEngine(Engine engine) {
		this.engine = engine;
		return this;
	}

	public TestContainerBuilder withEntryBeans(Class<?>... entryBeans) {
		this.entryBeans = entryBeans;
		return this;
	}

	public TestContainerBuilder withTestClass(Class<?> testClass) {
		this.testClass = testClass;
		return this;
	}

	public TestContainerBuilder withConfigOverrides(Map<String, String> configOverrides) {
		this.configOverrides = configOverrides;
		return this;
	}

	public BeanContainer build() {
		if (engine == Engine.AOT) {
			return buildAot();
		}
		return buildRuntime();
	}

	private BeanContainer buildAot() {
		if (testClass == null) {
			throw new IllegalStateException("testClass is required for AOT engine");
		}
		if (entryBeans != null && entryBeans.length > 0) {
			return loadAotLocalContext(testClass);
		}
		try {
			return DiEngine.resolve(Engine.AOT).create();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT context", e);
		}
	}

	private BeanContainer buildRuntime() {
		if (entryBeans != null && entryBeans.length > 0) {
			return RuntimeBeanContainerBuilder.buildFromSeeds(entryBeans);
		}
		return RuntimeBeanContainerBuilder.build();
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
