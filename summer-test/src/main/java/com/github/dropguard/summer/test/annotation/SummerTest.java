package com.github.dropguard.summer.test.annotation;

import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.test.internal.SummerExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test class as a Summer-managed test.
 *
 * <p>The test class follows the same constructor injection contract as {@code @Component}: exactly
 * one public constructor whose parameters are resolved from the application context.
 *
 * <p><b>Bean scope follows the Quarkus model — there is no narrowing.</b> A {@code @SummerTest}
 * container spans the <em>whole application universe</em>: every production bean across all
 * modules, <em>plus</em> every test bean on the test classpath (controllers, stub configs, route
 * fixtures). This is exactly the universe {@code @QuarkusTest} builds — there is no seed list, no
 * module whitelist, no package filter. A test bean is discovered exactly like a production bean: if
 * it is indexed and in scope, the container wires it.
 *
 * <p>Test isolation is achieved Quarkus-style, <em>never</em> by shrinking the discovery universe:
 *
 * <ul>
 *   <li>{@code @TestProfile} selects a configuration variant (different
 *       {@code @ConfigurationProperties} values) — see {@link
 *       com.github.dropguard.summer.test.annotation.TestProfile}.
 *   <li>{@code @Mock} on a constructor parameter swaps a real bean for a Mockito stub — see {@link
 *       com.github.dropguard.summer.test.annotation.Mock}.
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
 * <p>In dev mode (IDE, {@code mvn test}) the container is built with the Runtime engine. The engine
 * is intentionally invisible to tests; the {@link #engine()} attribute is the {@code Summer:dev}
 * debug escape hatch for forcing a specific engine without changing test code.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
public @interface SummerTest {

    /**
     * DI engine used to build the container. Transparent to tests in normal use (the framework
     * picks Runtime for dev and proves AOT parity via {@code @DualEngine}); this is the {@code
     * Summer:dev} debug escape hatch for forcing a specific engine without changing test code.
     *
     * <p>Default {@link Engine#RUNTIME} — AOT is reserved for production startup and dual-engine
     * verification, not day-to-day test runs.
     */
    Engine engine() default Engine.RUNTIME;

    /**
     * Optional seed classes for a narrow (scoped) test universe. When non-empty, the container is
     * built only from these classes plus their transitive dependency closure — equivalent to
     * Quarkus' {@code beanClasses(...)}. This is how negative tests (circular dependencies, missing
     * dependencies, ambiguous resolution) isolate an intentionally broken graph without pulling in
     * the whole application.
     *
     * <p>Leave empty (the default) to use the full Quarkus-style universe: every production bean
     * plus every test bean on the classpath. The two modes are mutually exclusive per test class; a
     * class either scopes itself or runs against the whole universe.
     */
    Class<?>[] classes() default {};

    /**
     * Marks the container build as expected to FAIL. Quarkus-aligned ({@code
     * ArcTestContainer.shouldFail(true)}): the test asserts that assembly throws, so a build
     * exception is the PASS condition and a successful build is the FAIL. Used for negative tests
     * (circular dependencies, missing dependencies, ambiguous resolution) without hand-rolled
     * try/catch.
     *
     * <p>On a {@code @DualEngine} test each engine is judged independently, which enforces that
     * both engines fail identically — a divergence (one engine throws a typed exception, the other
     * fails at code generation) surfaces as a per-engine failure rather than a silent
     * inconsistency.
     */
    boolean shouldFail() default false;
}
