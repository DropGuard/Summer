package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.core.DiEngine;
import summer.test.SummerExtension;

/**
 * JUnit 5 Extension annotation that manages the Summer ApplicationContext
 * lifecycle and automatically injects beans into test class fields annotated
 * with @Inject.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
public @interface SummerTest {

	/**
	 * The DI engine to use for the test context. The engine class must have a
	 * public no-arg constructor.
	 */
	Class<? extends DiEngine> value();
}
