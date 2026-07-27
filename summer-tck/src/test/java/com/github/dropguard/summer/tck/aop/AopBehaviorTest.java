package com.github.dropguard.summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.aop.ClassLevelGreeter;
import com.github.dropguard.summer.fixtures.aop.Greeter;
import com.github.dropguard.summer.fixtures.aop.RecordingInterceptor;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class AopBehaviorTest {

    private final BeanContainer context;

    public AopBehaviorTest(BeanContainer context) {
        this.context = context;
    }

    @DualEngine
    void testInterceptedMethodReturnsWrappedResult() {
        Greeter greeter = context.getBean(Greeter.class);
        String result = greeter.greet("Alice");
        assertEquals(
                "[intercepted] Hello, Alice",
                result,
                "Intercepted method must return the mutated value from the interceptor");
    }

    @DualEngine
    void testNonInterceptedMethodIsUnaffected() {
        Greeter greeter = context.getBean(Greeter.class);
        String result = greeter.shout("hello");
        assertEquals(
                "HELLO",
                result,
                "Non-intercepted method must return the raw result, without any prefix");
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
        ClassLevelGreeter greeter = context.getBean(ClassLevelGreeter.class);
        assertEquals(
                "[intercepted] Hello, Alice",
                greeter.greet("Alice"),
                "Class-level @Logged must intercept greet()");
        assertEquals(
                "[intercepted] HELLO",
                greeter.shout("hello"),
                "Class-level @Logged must intercept shout() even without method-level annotation");
    }
}
