package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.core.Engine;
import summer.test.SummerExtension;

/**
 * JUnit 5 extension for Summer integration tests (external dependencies:
 * databases, Redis, Docker containers, HTTP servers).
 *
 * <p>
 * Identical to {@link SummerTest} in behavior, but tagged
 * {@code "integration"} so Maven failsafe can pick it up separately
 * from unit tests.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
@Tag("integration")
public @interface SummerIntegrationTest {

    Class<?>[] value() default {};

    Engine engine() default Engine.RUNTIME;
}