package com.github.dropguard.summer.tck.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.fixtures.aop.ClassLevelGreeter;
import com.github.dropguard.summer.fixtures.aop.Greeter;
import com.github.dropguard.summer.fixtures.aop.GreeterService;
import com.github.dropguard.summer.fixtures.aop.RecordingInterceptor;
import com.github.dropguard.summer.test.TestContainer;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AOT-engine-specific pin for AOP interception behaviour.
 *
 * <p>{@link AopBehaviorTest} already exercises the same fixtures through {@code @DualEngine}
 * (Runtime + AOT). This test adds an explicit, engine-forced variant that builds the container with
 * {@link Engine#AOT} only, so the AOT proxy code path ({@code AotProxyGenerator}) is pinned on its
 * own rather than implicitly shared with the Runtime engine's {@code ProxyFactory}. It guards
 * against a regression where the two engines diverge on interceptor binding resolution (class-level
 * = all methods; method-level = that method only), matching the Quarkus/CDI contract that bindings
 * live on the implementation class/method.
 */
@SummerTest
public class AopBehaviorAotEngineTest {

    @Test
    void aotEngineHonoursImplementationClassAndMethodBindings() {
        BeanContainer context =
                TestContainer.builder().testClass(getClass()).engine(Engine.AOT).build();

        RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);
        interceptor.clearLog();

        // Method-level @Logged on GreeterService.greet must be intercepted.
        Greeter greeter = context.getBean(Greeter.class);
        assertEquals("Hello, Alice", greeter.greet("Alice"));
        assertEquals(List.of("before:greet", "after:greet"), interceptor.getCallLog());

        // Non-annotated method must pass through untouched.
        interceptor.clearLog();
        assertEquals("HELLO", greeter.shout("hello"));
        assertTrue(interceptor.getCallLog().isEmpty());

        // Returned bean must be a JDK proxy, not the raw impl.
        assertNotEquals(GreeterService.class, greeter.getClass());
        assertInstanceOf(Greeter.class, greeter);

        // Class-level @Logged must intercept EVERY method.
        interceptor.clearLog();
        ClassLevelGreeter classLevel = context.getBean(ClassLevelGreeter.class);
        assertEquals("Hello, Alice", classLevel.greet("Alice"));
        assertEquals("HELLO", classLevel.shout("hello"));
        assertEquals(
                List.of("before:greet", "after:greet", "before:shout", "after:shout"),
                interceptor.getCallLog());

        // AOP lookup contract (one bean, one form): the concrete-typed lookup of a BOUND bean
        // fails loudly on BOTH engines instead of returning the raw instance.
        interceptor.clearLog();
        assertThrows(
                com.github.dropguard.summer.core.exception.NoSuchBeanException.class,
                () -> context.getBean(GreeterService.class),
                "getBean(ConcreteClass.class) on a bound bean must fail loudly");
        assertTrue(interceptor.getCallLog().isEmpty());
    }
}
