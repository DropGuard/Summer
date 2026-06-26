package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.core.Engine;
import summer.test.SummerExtension;

/**
 * JUnit 5 extension that manages a Summer {@code BeanContainer} for the
 * annotated test class. The container is created once per test class and closed
 * automatically.
 *
 * <pre>
 * {@code
 * &#64;SummerTest                    // full classpath scan
 * &#64;SummerTest({CorsConfig.class}) // local expansion (only these beans)
 * &#64;SummerTest(engine = AOT)      // use AOT-generated context (TCK)
 * }
 * </pre>
 *
 * <p>
 * The container is injected via constructor parameter of type
 * {@code BeanContainer}.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
public @interface SummerTest {

	/**
	 * Entry bean classes for local expansion. When non-empty,
	 * {@code RuntimeBeanContainerBuilder.buildFromSeeds(...)} is used instead of a
	 * full Jandex scan.
	 */
	Class<?>[] value() default {};

	/**
	 * DI engine to use. Defaults to {@link Engine#RUNTIME}.
	 */
	Engine engine() default Engine.RUNTIME;
}