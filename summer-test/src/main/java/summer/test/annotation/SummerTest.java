package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.core.Engine;
import summer.test.SummerExtension;

/**
 * Marks a test class as a Summer-managed test.
 *
 * <p>
 * The test class follows the same constructor injection contract as
 * {@code @Component}: exactly one public constructor whose parameters are
 * resolved from the application context.
 * </p>
 *
 * <p>
 * <b>Bean scope follows the Quarkus model.</b> By default the container spans
 * the <em>whole production application</em> — every bean across all modules,
 * transitively (the same universe {@code @QuarkusTest} builds). A test sees all
 * production beans without declaring anything; there is no narrow-module
 * surprise and no manual seed list to maintain.
 * </p>
 *
 * <p>
 * Test isolation is achieved Quarkus-style, <em>not</em> by shrinking the
 * discovery universe:
 * </p>
 * <ul>
 * <li>{@code @TestProfile} selects a configuration variant (different
 * {@code @ConfigurationProperties} values) — see
 * {@link summer.test.annotation.TestProfile}.</li>
 * <li>{@code @Mock} on a constructor parameter swaps a real bean for a Mockito
 * stub — see {@link summer.test.annotation.Mock}.</li>
 * </ul>
 *
 * <p>
 * The {@link #modules()} / {@link #packages()} attributes are an <em>optional
 * narrowing</em> on top of that default: they shrink the universe to the named
 * modules or package trees when a test genuinely only needs a slice. They never
 * widen the universe beyond production beans, so they cannot pull in unrelated
 * test fixtures (the sad-path trap). When neither is set, the full
 * production application applies, matching {@code @QuarkusTest} semantics.
 * </p>
 *
 * <pre>{@code
 * // Quarkus-style: whole production app in scope, no declaration needed
 * &#64;SummerTest
 * class CorsConfigBindingTest {
 * 	CorsConfigBindingTest(CorsConfig config) { ... }
 * }
 *
 * // Optional narrowing: only the named module + its dependencies
 * &#64;SummerTest(modules = "summer-twitter")
 * class TwitterSliceTest { ... }
 *
 * // Optional narrowing: a single package tree
 * &#64;SummerTest(packages = "com.myapp.payment")
 * class PaymentSliceTest { ... }
 * }</pre>
 *
 * <p>
 * In dev mode (IDE, {@code mvn test}) the container is built with the Runtime
 * engine. The engine is intentionally invisible to tests; a dev-only switch can
 * be used to inspect the AOT assembly without changing test code.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
public @interface SummerTest {

	/**
	 * Additional module names whose beans should be included in the container,
	 * beyond the test class's own module. Used for cross-module integration tests.
	 * Module names match the {@code META-INF/jandex.idx} origin recorded by
	 * {@code JandexIndexLoader}.
	 *
	 * <p>
	 * Empty by default — only the test's own module (and its transitive
	 * dependencies) is scanned.
	 * </p>
	 */
	String[] modules() default {};

	/**
	 * Package prefixes whose {@code @Component} classes should be included in the
	 * container, regardless of module boundaries. A narrower alternative to
	 * {@link #modules()} when only a sub-tree of a dependency is needed.
	 *
	 * <p>
	 * Empty by default.
	 * </p>
	 */
	String[] packages() default {};

	/**
	 * DI engine used to build the container. Transparent to tests in normal use
	 * (the framework picks Runtime for dev and proves AOT parity via
	 * {@code @DualEngine}); this is the {@code Summer:dev} debug escape hatch for
	 * forcing a specific engine without changing test code.
	 *
	 * <p>
	 * Default {@link Engine#RUNTIME} — AOT is reserved for production startup and
	 * dual-engine verification, not day-to-day test runs.
	 * </p>
	 */
	Engine engine() default Engine.RUNTIME;
}
