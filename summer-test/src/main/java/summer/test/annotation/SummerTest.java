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
 * <b>Bean scope follows the Quarkus model — there is no narrowing.</b> A
 * {@code @SummerTest} container spans the <em>whole application universe</em>:
 * every production bean across all modules, <em>plus</em> every test bean on
 * the test classpath (controllers, stub configs, route fixtures). This is
 * exactly the universe {@code @QuarkusTest} builds — there is no seed list, no
 * module whitelist, no package filter. A test bean is discovered exactly like a
 * production bean: if it is indexed and in scope, the container wires it.
 * </p>
 *
 * <p>
 * Test isolation is achieved Quarkus-style, <em>never</em> by shrinking the
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
 * <pre>{@code
 * // Quarkus-style: whole application + test beans in scope, no declaration needed
 * &#64;SummerTest
 * class CorsConfigBindingTest {
 * 	CorsConfigBindingTest(CorsConfig config) { ... }
 * }
 * }</pre>
 *
 * <p>
 * In dev mode (IDE, {@code mvn test}) the container is built with the Runtime
 * engine. The engine is intentionally invisible to tests; the {@link #engine()}
 * attribute is the {@code Summer:dev} debug escape hatch for forcing a specific
 * engine without changing test code.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
public @interface SummerTest {

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
