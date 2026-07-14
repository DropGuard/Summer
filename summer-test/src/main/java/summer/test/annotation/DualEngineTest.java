package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.test.DualEngineExtension;

/**
 * TCK annotation that runs each test method against both the Runtime and AOT DI
 * engines.
 *
 * <p>
 * The test class must have exactly one public constructor that accepts
 * {@link summer.core.BeanContainer}. The extension creates two instances of the
 * test class — one per engine — and runs each {@code @Test} method on both.
 * </p>
 *
 * <pre>{@code
 * &#64;DualEngineTest(seeds = {ServiceA.class})
 * class DependencyInjectionBehavior {
 *     private final BeanContainer context;
 *     DependencyInjectionBehavior(BeanContainer context) { this.context = context; }
 *
 *     &#64;Test
 *     void testSingletonUniqueness() { ... }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DualEngineExtension.class)
public @interface DualEngineTest {

	/** Seed classes for transitive dependency expansion. */
	Class<?>[] seeds() default {};
}
