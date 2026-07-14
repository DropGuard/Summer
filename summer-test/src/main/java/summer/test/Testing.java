package summer.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.IndexView;
import summer.core.BeanContainer;
import summer.core.Component;
import summer.core.Engine;
import summer.core.bean.ModuleIndex;
import summer.core.bean.Scope;
import summer.runtime.JandexIndexLoader;
import summer.runtime.RuntimeBeanContainerBuilder;

/**
 * Unified test container builder for Summer DI engines.
 *
 * <p>
 * <b>User path</b> (engine transparent — always Runtime in dev mode):
 * </p>
 *
 * <pre>{@code
 * Testing.build();
 * Testing.build(AppConfig.class);
 * Testing.buildWithExternal(new Class<?>[]{AppConfig.class}, someBean);
 * Testing.scan("com.myapp");
 * }</pre>
 *
 * <p>
 * <b>TCK path</b> (explicit engine for dual-engine verification):
 * </p>
 *
 * <pre>{@code
 * Testing.build(Engine.RUNTIME, seeds);
 * Testing.build(Engine.AOT, seeds);
 * }</pre>
 */
public final class Testing {

	private Testing() {
	}

	// ── User path: engine transparent ───────────────────────────────────

	/** Builds a container using the Runtime engine (dev mode default). */
	public static BeanContainer build(Class<?>... seeds) {
		return build(Engine.RUNTIME, seeds);
	}

	public static BeanContainer buildWithExternal(Class<?>[] seeds, Object... externalBeans) {
		if (seeds.length > 0) {
			return RuntimeBeanContainerBuilder.buildFromSeedsWithExternal(seeds, externalBeans);
		}
		return RuntimeBeanContainerBuilder.build(externalBeans);
	}

	/**
	 * Auto-scans a package tree for components. {@code seeds} are additional
	 * classes outside the target package.
	 */
	public static BeanContainer scan(String basePackage, Class<?>... seeds) {
		return RuntimeBeanContainerBuilder.buildModuleWithExternal(basePackage, seeds);
	}

	// ── TCK path: explicit engine ───────────────────────────────────

	public static BeanContainer build(Engine engine, Class<?>... seeds) {
		if (engine == Engine.RUNTIME) {
			return buildFromSeeds(seeds);
		}
		return buildAot(seeds);
	}

	// ── Utility ─────────────────────────────────────────────────────

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

	// ── Module detection ────────────────────────────────────────────

	/**
	 * Builds a container scoped to the test class's own module.
	 *
	 * <p>
	 * Used by {@code @SummerTest}. Automatically detects which Maven module
	 * the test class belongs to via {@link ModuleIndex} and scopes bean
	 * discovery to that module. Falls back to the full merged index if the
	 * test class is not found in any indexed module.
	 * </p>
	 *
	 * @param testClass
	 *            the test class to scope the container for
	 * @param externalBeans
	 *            pre-instantiated beans to register (e.g. mocks)
	 * @return immutable bean container
	 */
	public static BeanContainer buildForTest(Class<?> testClass, Object... externalBeans) {
		ModuleIndex moduleIndex = JandexIndexLoader.buildModuleIndex();
		String module = moduleIndex.moduleOf(testClass.getName());
		if (module != null) {
			return RuntimeBeanContainerBuilder.buildFromModuleScope(moduleIndex, List.of(module), externalBeans);
		}
		return RuntimeBeanContainerBuilder.build(externalBeans);
	}

	// ── Internals ───────────────────────────────────────────────────

	private static BeanContainer buildFromSeeds(Class<?>... seeds) {
		return seeds.length > 0
				? RuntimeBeanContainerBuilder.buildFromSeeds(seeds)
				: RuntimeBeanContainerBuilder.build();
	}

	private static BeanContainer buildAot(Class<?>... seeds) {
		try {
			IndexView index = JandexIndexLoader.buildIndex();
			// Deterministic cache key from seed names. Never use identity hash
			// of a lambda scope — that changes on every JVM invocation.
			Set<String> seedNames = toNames(seeds);
			String cacheKey = seeds.length > 0
					? "aot-" + String.join(",", seedNames.stream().sorted().toList())
					: "aot-classpath";

			// visibleTypes: all indexed class names for cross-module
			// @ConditionalOnBean visibility.
			Scope scope = seeds.length > 0 ? seedNames::contains : Scope.classpath();
			java.util.HashSet<String> visibleTypes = new java.util.HashSet<>();
			for (org.jboss.jandex.ClassInfo ci : index.getKnownClasses()) {
				visibleTypes.add(ci.name().toString());
			}

			Class<?> aotEngine = Class.forName("summer.aot.AotEngine");
			Method buildAndCompile = aotEngine.getMethod("buildAndCompile",
					IndexView.class, Scope.class, java.util.Set.class, String.class, Object[].class);
			return (BeanContainer) buildAndCompile.invoke(null, index, scope, visibleTypes, cacheKey, new Object[0]);

		} catch (Exception e) {
			throw new RuntimeException("Failed to build AOT container. Ensure summer-aot-engine is on the classpath.",
					e);
		}
	}

	private static Set<String> toNames(Class<?>... seeds) {
		Set<String> names = new LinkedHashSet<>();
		for (Class<?> seed : seeds) {
			names.add(seed.getName());
		}
		return names;
	}
}
