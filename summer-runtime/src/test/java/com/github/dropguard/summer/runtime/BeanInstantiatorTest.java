package com.github.dropguard.summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BeanInstantiatorTest {

    @Test
    void shouldWrapConstructorException() {
        BeanContainer.Builder builder = new BeanContainer.Builder();
        BeanInstantiator instantiator =
                new BeanInstantiator(
                        builder, Map.of(), Map.of(), Map.of(), new InstantiatedBeans(Map.of()));

        BeanDefinition def =
                new BeanDefinition(CrashingComponent.class.getName(), "crashingComponent");

        BeanCreationException ex =
                assertThrows(
                        BeanCreationException.class,
                        () -> instantiator.instantiateFromDefinition(def));

        assertTrue(ex.getMessage().contains("Failed to instantiate bean"));
        assertNotNull(ex.getCause());
        assertEquals("Crash", ex.getCause().getCause().getMessage());
    }

    @Test
    void shouldWrapClassNotFoundException() {
        BeanContainer.Builder builder = new BeanContainer.Builder();
        BeanInstantiator instantiator =
                new BeanInstantiator(
                        builder, Map.of(), Map.of(), Map.of(), new InstantiatedBeans(Map.of()));

        // A class name under the project's negative-fixtures namespace that is
        // intentionally never registered as a bean, so resolution fails with a clear
        // ClassNotFoundException. (Kept as a string literal, not a real class, because
        // the test asserts the "not found" path.)
        BeanDefinition def =
                new BeanDefinition(
                        "com.github.dropguard.summer.tck.invisible.fixtures.di.MissingBean",
                        "missing");

        BeanCreationException ex =
                assertThrows(
                        BeanCreationException.class,
                        () -> instantiator.instantiateFromDefinition(def));

        assertTrue(
                ex.getMessage()
                        .contains(
                                "Class not found:"
                                    + " com.github.dropguard.summer.tck.invisible.fixtures.di.MissingBean"));
        assertTrue(ex.getCause() instanceof ClassNotFoundException);
    }

    public static class CrashingComponent {
        public CrashingComponent() {
            throw new RuntimeException("Crash");
        }
    }
}
