package com.github.dropguard.summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import com.github.dropguard.summer.fixtures.aop.ClassLevelGreeter;
import com.github.dropguard.summer.fixtures.aop.Greeter;
import com.github.dropguard.summer.fixtures.aop.RecordingInterceptor;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;

@SummerTest
public class AopBehaviorTest {

    @DualEngine
    void testInterceptedMethodFiresInterceptor(BeanContainer context) {
        RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);
        interceptor.clearLog();

        Greeter greeter = context.getBean(Greeter.class);
        String result = greeter.greet("Alice");
        assertEquals("Hello, Alice", result);

        assertEquals(
                List.of("before:greet", "after:greet"),
                interceptor.getCallLog(),
                "Interceptor must record before/after calls");
    }

    @DualEngine
    void testNonInterceptedMethodIsUnaffected(BeanContainer context) {
        RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);
        interceptor.clearLog();

        Greeter greeter = context.getBean(Greeter.class);
        String result = greeter.shout("hello");
        assertEquals("HELLO", result);
        assertTrue(
                interceptor.getCallLog().isEmpty(),
                "Interceptor must not fire for non-@Logged methods");
    }

    @DualEngine
    void testBeanIsProxy(BeanContainer context) {
        Greeter greeter = context.getBean(Greeter.class);
        assertNotEquals(
                com.github.dropguard.summer.fixtures.aop.GreeterService.class,
                greeter.getClass(),
                "The bean returned from context must be a proxy, not the raw GreeterService");
        assertInstanceOf(Greeter.class, greeter);
    }

    @DualEngine
    void testConcreteClassLookupFailsLoudlyForBoundBean(BeanContainer context) {
        RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);
        interceptor.clearLog();

        // AOP lookup contract (one bean, one form): the bound bean exists ONLY as its proxy,
        // which is not an instance of the concrete class — so a concrete-typed lookup must fail
        // LOUDLY instead of silently handing out the un-intercepted raw instance. Declare the
        // dependency on the interface (Greeter) instead.
        NoSuchBeanException thrown =
                assertThrows(
                        NoSuchBeanException.class,
                        () ->
                                context.getBean(
                                        com.github.dropguard.summer.fixtures.aop.GreeterService
                                                .class),
                        "getBean(ConcreteClass.class) on a bound bean must fail loudly, not"
                                + " return the raw instance");
        assertTrue(
                thrown.getMessage().contains("GreeterService"),
                "the failure must name the unresolved type: " + thrown.getMessage());
        assertTrue(
                interceptor.getCallLog().isEmpty(),
                "no interception may fire during the failed lookup");
    }

    @DualEngine
    void testClassLevelBindingInterceptsAllMethods(BeanContainer context) {
        RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);
        interceptor.clearLog();

        ClassLevelGreeter greeter = context.getBean(ClassLevelGreeter.class);
        assertEquals("Hello, Alice", greeter.greet("Alice"));
        assertEquals("HELLO", greeter.shout("hello"));

        assertEquals(
                List.of("before:greet", "after:greet", "before:shout", "after:shout"),
                interceptor.getCallLog(),
                "Class-level @Logged must intercept all methods");
    }
}
