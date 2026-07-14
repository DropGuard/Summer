package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.test.SummerExtension;

/**
 * Marks a test class as a Summer-managed test.
 *
 * <p>
 * The test class follows the same constructor injection contract as
 * {@code @Component}: exactly one public constructor whose parameters are
 * resolved from the application context. The container is scoped to the
 * test class's own Maven module (detected automatically from the Jandex
 * index).
 * </p>
 *
 * <pre>{@code
 * &#64;SummerTest
 * class CorsConfigBindingTest {
 * 	CorsConfigBindingTest(CorsConfig config) {
 * 		// config is injected directly — no getBean()
 * 	}
 * }
 * }</pre>
 *
 * <p>
 * In dev mode (IDE, {@code mvn test}), the container is built using the Runtime
 * DI engine.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
public @interface SummerTest {
}
