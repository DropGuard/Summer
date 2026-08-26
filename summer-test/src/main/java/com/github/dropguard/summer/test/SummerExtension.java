package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;

/**
 * JUnit 5 extension for {@link SummerTest}.
 *
 * <p>Builds a {@link BeanContainer} during test instance creation and resolves constructor
 * parameters against it — same injection contract as {@code @Component}.
 */
@Internal
public class SummerExtension
        implements TestInstanceFactory, org.junit.jupiter.api.extension.ParameterResolver {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(SummerExtension.class);
    private static final String KEY = "BeanContainer";

    @Override
    public Object createTestInstance(
            TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext)
            throws TestInstantiationException {
        Class<?> testClass = factoryContext.getTestClass();
        SummerTest summerTest = testClass.getAnnotation(SummerTest.class);
        if (summerTest == null) {
            return null;
        }

        SummerTestLifecycle.BuildOutcome outcome =
                SummerTestLifecycle.createUniverse(testClass, Engine.RUNTIME);
        extensionContext.getStore(NS).put(KEY, outcome.container());

        return outcome.instance();
    }

    // ── Mockito bridge ──────────────────────────────────────────

    static Object createMock(Class<?> type) {
        try {
            Class<?> mockito = Class.forName("org.mockito.Mockito");
            Method mockMethod = mockito.getMethod("mock", Class.class);
            return mockMethod.invoke(null, type);
        } catch (ClassNotFoundException e) {
            throw new TestInstantiationException(
                    "@Mock requires Mockito on the classpath. Add org.mockito:mockito-core as a"
                            + " test dependency.");
        } catch (Exception e) {
            throw new TestInstantiationException(
                    "Failed to create mock for " + type.getSimpleName(), e);
        }
    }

    // ── BeanContainer parameter resolution (RUNTIME universe only) ──────
    //
    // Serves plain tests whose lifecycle or test methods declare a BeanContainer parameter —
    // always the RUNTIME universe's container. Inside a @DualEngine test-method invocation this
    // resolver declines, leaving the parameter to the invocation-scoped EngineParameterResolver;
    // the two resolvers therefore never compete for one parameter.
    @Override
    public boolean supportsParameter(
            org.junit.jupiter.api.extension.ParameterContext pc,
            org.junit.jupiter.api.extension.ExtensionContext ec) {
        return pc.getParameter().getType() == BeanContainer.class
                && !DualEngineInvocationProvider.isEngineInvocation(ec);
    }

    @Override
    public Object resolveParameter(
            org.junit.jupiter.api.extension.ParameterContext pc,
            org.junit.jupiter.api.extension.ExtensionContext ec) {
        var c = ec.getStore(NS).get(KEY);
        if (c == null) {
            throw new org.junit.jupiter.api.extension.ExtensionConfigurationException(
                    "No RUNTIME container available for BeanContainer parameter");
        }
        return c;
    }
}
