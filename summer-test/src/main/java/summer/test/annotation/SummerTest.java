package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.core.ApplicationContext;
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
	 * The ApplicationContext implementation to use for the test. The class must
	 * have a public no-arg constructor and a static {@code create(Class<?>)}
	 * method.
	 */
	Class<? extends ApplicationContext> value();
	/**
	 * When true, all {@link summer.core.ApplicationRunner} beans are started after
	 * the context is initialized (e.g. Netty HTTP server, gRPC server). Defaults to
	 * false — only DI initialization.
	 */
	boolean web() default false;
}
