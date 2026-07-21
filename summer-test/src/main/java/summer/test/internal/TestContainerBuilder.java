package summer.test.internal;
import java.lang.annotation.Annotation;
import summer.core.BeanContainer;
import summer.core.Component;
import summer.core.DiEngine;
import summer.core.Internal;
import summer.test.Testing;

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
 * <p>
 * The two user-facing methods below are the complete surface:
 * </p>
 *
 * <pre>{@code
 * // User path — engine transparent (Runtime in dev mode):
 * TestContainerBuilder.build();
 *
 * // TCK path — explicit AOT engine for dual-engine verification:
 * TestContainerBuilder.buildAot(); // full AOT context (production-equivalent)
 * }</pre>
 *
 * <p>
 * Mocks are never passed in as a hand-rolled instance collection (a concept
 * Quarkus does not expose). They are declared with {@code @Mock} on a
 * {@code @SummerTest} constructor parameter and supplied by the framework — the
 * same mechanism the dual-engine path uses internally.
 * </p>
 */
@Internal
public final class TestContainerBuilder {

	private TestContainerBuilder() {
	}

	// ── User path: engine transparent ───────────────────────────────────

	/**
	 * Builds a container over the full test universe using the auto-detected engine
	 * (Runtime in dev mode).
	 */
	public static BeanContainer build() {
		return Testing.build();
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
