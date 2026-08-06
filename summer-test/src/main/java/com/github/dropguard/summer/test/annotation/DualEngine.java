package com.github.dropguard.summer.test.annotation;

import com.github.dropguard.summer.test.DualEngineInvocationProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Method-level trigger for a dual-engine behavioural test.
 *
 * <p>Replaces {@code @Test} on a method inside a {@link SummerTest}-annotated class. Because
 * JUnit's {@link TestTemplate} only acts on a method, the per-engine invocation is driven here —
 * the enclosing {@code DualEngineInvocationProvider} (registered by this annotation) runs the
 * method once per DI engine (Runtime and AOT), so the test proves both engines behave identically.
 *
 * <p><strong>ApplicationRunner caveat:</strong> each leg builds its own container, and a container
 * that includes an {@link com.github.dropguard.summer.core.ApplicationRunner} (e.g. the HTTP
 * server) starts it — so a {@code @DualEngine} test whose universe contains a server must configure
 * an ephemeral port ({@code server.port: 0}) and read the actual port from the runner bean ({@code
 * context.getBean(NettyServerRunner.class).getPort()}). With a fixed port, the RUNTIME leg binds it
 * and the AOT leg fails with an "address already in use" diagnostic.
 *
 * <pre>{@code
 * &#64;SummerTest
 * class BeanReplacementTest {
 *     &#64;DualEngine
 *     void replacesCorrectly() { ... }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(DualEngineInvocationProvider.class)
public @interface DualEngine {}
