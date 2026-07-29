package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.Internal;
import java.lang.reflect.Field;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * A JUnit 5 {@code @RegisterExtension} that configures a Summer DI container with explicit seed
 * bean classes and optional expected-failure — Quarkus' {@code ArcTestContainer} model.
 *
 * <p>Implements JUnit lifecycle callbacks (no-ops) only to satisfy {@code @RegisterExtension}
 * requirements. Config is resolved via reflection because {@code TestInstanceFactory} fires before
 * {@code BeforeAllCallback}.
 *
 * <pre>{@code
 * class SelfInjectionTest {
 *     &#64;RegisterExtension
 *     static SummerTestExtension ext = SummerTestExtension.builder()
 *         .beanClasses(SelfInjectingBean.class)
 *         .shouldFail()
 *         .build();
 *
 *     &#64;DualEngine
 *     void selfInjectionRejected() {}
 * }
 * }</pre>
 */
@Internal
public final class SummerTestExtension implements BeforeAllCallback, AfterAllCallback {

    private final Class<?>[] beanClasses;
    private final boolean shouldFail;

    private SummerTestExtension(Class<?>[] beanClasses, boolean shouldFail) {
        this.beanClasses = beanClasses;
        this.shouldFail = shouldFail;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Class<?>[] beanClasses = new Class<?>[0];
        private boolean shouldFail;

        public Builder beanClasses(Class<?>... classes) {
            this.beanClasses = classes;
            return this;
        }

        public Builder shouldFail() {
            this.shouldFail = true;
            return this;
        }

        public SummerTestExtension build() {
            return new SummerTestExtension(beanClasses, shouldFail);
        }
    }

    public Class<?>[] beanClasses() {
        return beanClasses;
    }

    public boolean shouldFail() {
        return shouldFail;
    }

    // Satisfy @RegisterExtension contract — no-ops, config is read via reflection.
    @Override
    public void beforeAll(ExtensionContext ctx) {}

    @Override
    public void afterAll(ExtensionContext ctx) {}

    /**
     * Finds the {@code SummerTestExtension} declared via {@code @RegisterExtension} on the test
     * class. Returns null if absent.
     */
    public static SummerTestExtension resolve(Class<?> testClass) {
        return findOn(testClass);
    }

    private static SummerTestExtension findOn(Class<?> testClass) {
        for (Field field : testClass.getDeclaredFields()) {
            if (field.getType() == SummerTestExtension.class
                    && field.isAnnotationPresent(RegisterExtension.class)) {
                try {
                    field.setAccessible(true);
                    return (SummerTestExtension) field.get(null);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return null;
    }
}
