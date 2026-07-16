package summer.test;

import java.lang.annotation.Annotation;
import summer.core.BeanContainer;
import summer.core.Component;
import summer.core.DiEngine;
import summer.runtime.RuntimeBeanContainerBuilder;

/**
 * Test container builder for Summer DI engines.
 *
 * <p>
 * Every entry point builds over the <b>test universe</b> — the whole
 * application (production beans across all modules) plus whatever test beans
 * are on the classpath. This mirrors {@code @QuarkusTest}: there is no seed
 * list, no module narrowing, no package filter. A test bean is discovered
 * exactly like a production bean. Isolation between tests comes from
 * {@code @TestProfile} and {@code @Mock}, never from shrinking the discovery
 * universe.
 * </p>
 *
 * <pre>{@code
 * // User path — engine transparent (Runtime in dev mode):
 * TestContainerBuilder.build();
 * TestContainerBuilder.buildWithExternal(ext);
 *
 * // TCK path — explicit engine for dual-engine verification:
 * TestContainerBuilder.buildAot(); // full AOT context (production-equivalent)
 * TestContainerBuilder.buildAotWithExternal(chain); // full AOT context + external beans
 * }</pre>
 */
public final class TestContainerBuilder {

	private TestContainerBuilder() {
	}

	// ── User path: engine transparent ───────────────────────────────────

	/**
	 * Builds a container over the full test universe using the auto-detected engine
	 * (Runtime in dev mode).
	 */
	public static BeanContainer build() {
		return RuntimeBeanContainerBuilder.build();
	}

	/**
	 * Builds a container over the full test universe, registering pre-instantiated
	 * {@code externalBeans} (e.g. mocks).
	 */
	public static BeanContainer buildWithExternal(Object... externalBeans) {
		return RuntimeBeanContainerBuilder.build(externalBeans);
	}

	// ── TCK path: explicit AOT engine ───────────────────────────────────

	/**
	 * Loads the full AOT-generated context ({@code GeneratedAotContext}).
	 * Equivalent to {@code Engine.AOT} production startup: the whole application
	 * universe, generated and compiled by {@code summer-maven-plugin}.
	 */
	public static BeanContainer buildAot() {
		try {
			return DiEngine.create();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT context", e);
		}
	}

	/**
	 * Loads the full AOT-generated context with external bean injection. External
	 * beans (e.g. Mockito mocks produced from {@code @Mock}) are registered
	 * <em>after</em> the generated wiring completes, overriding the real beans.
	 */
	public static BeanContainer buildAotWithExternal(Object... externalBeans) {
		try {
			return DiEngine.create(externalBeans);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT context with external beans", e);
		}
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
