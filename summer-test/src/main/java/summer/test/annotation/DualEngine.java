package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.test.internal.DualEngineInvocationProvider;

/**
 * Method-level trigger for a dual-engine behavioural test.
 *
 * <p>
 * Replaces {@code @Test} on a method inside a {@link SummerTest}-annotated
 * class. Because JUnit's {@link TestTemplate} only acts on a method, the
 * per-engine invocation is driven here — the enclosing
 * {@code DualEngineInvocationProvider} (registered by this annotation) runs the
 * method once per DI engine (Runtime and AOT), so the test proves both engines
 * behave identically.
 * </p>
 *
 * <pre>
 * {@code
 * &#64;SummerTest
 * class BeanReplacementTest {
 *     &#64;DualEngine
 *     void replacesCorrectly() { ... }
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(DualEngineInvocationProvider.class)
public @interface DualEngine {
}
