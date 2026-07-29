package com.github.dropguard.summer.test.internal;

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
public class SummerExtension implements TestInstanceFactory {

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
                SummerTestLifecycle.createUniverse(testClass, Engine.RUNTIME, extensionContext);
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
}
