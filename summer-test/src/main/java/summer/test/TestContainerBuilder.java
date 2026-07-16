package summer.test;

import java.lang.annotation.Annotation;
import summer.core.BeanContainer;
import summer.core.Component;
import summer.core.DiEngine;

/**
 * Test container builder for Summer DI engines.
 *
 * <p>
 * <b>User path</b> (engine transparent — always Runtime in dev mode):
 * </p>
 *
 * <pre>{@code
 * TestContainerBuilder.build();
 * TestContainerBuilder.build(AppConfig.class);
 * TestContainerBuilder.buildWithExternal(new Class<?>[]{AppConfig.class}, someBean);
 * }</pre>
 *
 * <p>
 * <b>TCK path</b> (explicit engine for dual-engine verification):
 * </p>
 *
 * <pre>{@code
 * TestContainerBuilder.buildRuntime();
 * TestContainerBuilder.buildRuntime(AppConfig.class);
 * TestContainerBuilder.buildRuntimeWithExternal(new Class<?>[]{AppConfig.class}, ext);
 *
 * TestContainerBuilder.buildAot(); // GeneratedAotContext (full, production-equivalent)
 * TestContainerBuilder.buildAotWithExternal(chain); // GeneratedAotContext + external
 * }</pre>
 */
public final class TestContainerBuilder {

	private TestContainerBuilder() {
	}

	// ── User path: engine transparent ───────────────────────────────────

	/** Builds a container using the auto-detected engine (Runtime in dev mode). */
	public static BeanContainer build(Class<?>... seeds) {
		return buildRuntime(seeds);
	}

	/** Builds a container with external beans using the auto-detected engine. */
	public static BeanContainer buildWithExternal(Class<?>[] seeds, Object... externalBeans) {
		return buildRuntimeWithExternal(seeds, externalBeans);
	}

	// ── TCK path: explicit Runtime engine ───────────────────────────────

	/**
	 * Builds a Runtime engine container.
	 *
	 * <p>
	 * With no arguments, builds from the full merged index. With seed classes, uses
	 * the exact seed scope (no transitive expansion).
	 * </p>
	 */
	public static BeanContainer buildRuntime(Class<?>... seeds) {
		return buildRuntimeWithExternal(seeds, new Object[0]);
	}

	public static BeanContainer buildRuntimeWithExternal(Class<?>[] seeds, Object... externalBeans) {
		if (seeds.length > 0) {
			return summer.runtime.RuntimeBeanContainerBuilder.buildFromSeedsWithExternal(seeds, externalBeans);
		}
		return summer.runtime.RuntimeBeanContainerBuilder.build(externalBeans);
	}

	// ── TCK path: explicit AOT engine ───────────────────────────────────

	/**
	 * Loads the full AOT-generated context ({@code GeneratedAotContext}).
	 * Equivalent to {@code Engine.AOT} production startup.
	 */
	public static BeanContainer buildAot() {
		try {
			return DiEngine.create();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT context", e);
		}
	}

	/**
	 * Loads a per-test AOT LocalContext for a specific test class, if one was
	 * generated at build time by {@code summer-maven-plugin}.
	 *
	 * <p>
	 * Historical note: this path was used by the retired {@code @WithFixtures}
	 * mechanism. After the test-infra redesign, scoped tests rely on
	 * {@code @SummerTest(modules = ...)} + module-derived scope (Runtime engine),
	 * and AOT verification uses the full generated context via {@link #buildAot()}.
	 * The LocalContext generation step was removed from {@code SummerMojo}, so this
	 * method is now a compatibility shim: it succeeds only if a
	 * {@code LocalContext_<testClassName>} class still exists on the classpath.
	 * </p>
	 */
	public static BeanContainer buildAot(Class<?> testClass) {
		String aotClassName = "summer.core.aot.LocalContext_" + testClass.getName().replace('.', '_').replace('$', '_');
		try {
			Class<?> aotClass = Class.forName(aotClassName);
			return (BeanContainer) aotClass.getMethod("build").invoke(null);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(
					"AOT LocalContext not found for " + testClass.getName()
							+ ". The test-scoped AOT LocalContext path has been retired; use @SummerTest(modules = ...)"
							+ " for scoped Runtime tests or TestContainerBuilder.buildAot() for the full AOT context.",
					e);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT LocalContext for " + testClass.getName(), e);
		}
	}

	/**
	 * Loads the full AOT-generated context with external bean injection.
	 * <p>
	 * External beans are registered <em>after</em> the generated wiring completes.
	 * </p>
	 */
	public static BeanContainer buildAotWithExternal(Object... externalBeans) {
		try {
			return DiEngine.create(externalBeans);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT context with external beans", e);
		}
	}

	/**
	 * Builds a Runtime engine container by auto-scanning a package tree.
	 *
	 * <p>
	 * All {@code @Component} (and meta-annotated) classes under {@code basePackage}
	 * are discovered automatically. Additional seeds outside the package tree (e.g.
	 * framework infrastructure) are passed explicitly.
	 * </p>
	 *
	 * <pre>{@code
	 * TestContainerBuilder.buildModuleWithExternal("summer.twitter", new Class<?>[]{NettyServerConfiguration.class},
	 * 		chain);
	 * }</pre>
	 */
	public static BeanContainer buildModuleWithExternal(String basePackage, Class<?>[] seeds, Object... externalBeans) {
		return summer.runtime.RuntimeBeanContainerBuilder.buildModuleWithExternal(basePackage, seeds, externalBeans);
	}

	/**
	 * Builds a Runtime engine container by auto-scanning a package tree. No
	 * external beans required.
	 */
	public static BeanContainer buildModule(String basePackage) {
		return buildModuleWithExternal(basePackage, new Class<?>[0]);
	}

	/**
	 * Builds a Runtime engine container by auto-scanning a package tree with
	 * additional seeds but no external beans.
	 */
	public static BeanContainer buildModule(String basePackage, Class<?>... seeds) {
		return buildModuleWithExternal(basePackage, seeds);
	}

	/**
	 * Checks whether a class has {@code @Component} or a meta-annotation that is
	 * itself annotated with {@code @Component} (e.g. {@code @RestController},
	 * {@code @Configuration}).
	 */
	public static boolean isComponent(Class<?> clazz) {
		if (clazz.isAnnotationPresent(Component.class)) {
			return true;
		}
		for (Annotation ann : clazz.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(Component.class)) {
				return true;
			}
		}
		return false;
	}
}
