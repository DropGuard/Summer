package com.github.dropguard.summer.test.annotation;

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
 * <p><b>Bean scope.</b> By default, a {@code @SummerTest} container spans the full application
 * universe. Narrow scoping (seed bean classes, expected-failure assertion) is handled by the {@link
 * com.github.dropguard.summer.test.SummerTestExtension} builder, which is declared as a
 * {@code @RegisterExtension} static field.
 *
 * <p>Test isolation:
 *
 * <ul>
 *   <li>{@code @TestProfile} selects a configuration variant.
 *   <li>{@code @Mock} on a constructor parameter swaps a real bean for a Mockito stub.
 *   <li>{@code @DualEngine} runs the test method on both Runtime and AOT engines.
 * </ul>
 *
 * <pre>{@code
 * &#64;SummerTest
 * &#64;TestProfile(DevProfile.class)
 * class CorsConfigBindingTest {
 *     CorsConfigBindingTest(CorsConfig config) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
public @interface SummerTest {}
