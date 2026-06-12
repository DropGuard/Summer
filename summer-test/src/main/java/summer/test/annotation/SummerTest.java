package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.core.Engine;
import summer.test.SummerExtension;

/**
 * JUnit 5 annotation that manages the Summer {@code ApplicationContext}
 * lifecycle and automatically injects beans into test methods.
 *
 * <pre>
 * {
 * 	&#64;code
 * 	&#64;SummerTest // defaults to AOT
 * 	class MyTest {
 * 		ApplicationContext context;
 *
 * 		@Test
 * 		void test(Foo foo) { // auto-resolved from context
 * 		}
 * 	}
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
public @interface SummerTest {
	/**
	 * DI engine to use. Defaults to {@link Engine#AOT}.
	 */
	Engine engine() default Engine.AOT;
}
