package com.github.dropguard.summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.aop.ClassLevelGreeter;
import com.github.dropguard.summer.fixtures.aop.Greeter;
import com.github.dropguard.summer.fixtures.aop.RecordingInterceptor;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;

@SummerTest
public class AopBehaviorTest {

    private final BeanContainer context;

    public AopBehaviorTest(BeanContainer context) {
        this.context = context;
    }

    @DualEngine
    void testInterceptedMethodFiresInterceptor() {
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
    void testNonInterceptedMethodIsUnaffected() {
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
    void testBeanIsProxy() {
        Greeter greeter = context.getBean(Greeter.class);
        assertNotEquals(
                com.github.dropguard.summer.fixtures.aop.GreeterService.class,
                greeter.getClass(),
                "The bean returned from context must be a proxy, not the raw GreeterService");
        assertInstanceOf(Greeter.class, greeter);
    }

    @DualEngine
    void testConcreteClassBypassesAop() {
        RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);
        interceptor.clearLog();

        com.github.dropguard.summer.fixtures.aop.GreeterService raw =
                context.getBean(com.github.dropguard.summer.fixtures.aop.GreeterService.class);
        assertEquals(
                com.github.dropguard.summer.fixtures.aop.GreeterService.class,
                raw.getClass(),
                "getBean(ConcreteClass.class) must return the raw instance, not a JDK proxy");

        String result = raw.greet("Charlie");
        assertEquals("Hello, Charlie", result, "greet() on raw instance must NOT be intercepted");
        assertTrue(
                interceptor.getCallLog().isEmpty(),
                "Interceptor must not fire when method is called on the raw instance");
    }

    @DualEngine
    void testClassLevelBindingInterceptsAllMethods() {
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
