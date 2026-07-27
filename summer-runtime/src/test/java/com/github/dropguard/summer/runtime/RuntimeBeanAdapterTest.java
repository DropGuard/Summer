package com.github.dropguard.summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.exception.UnsupportedInjectionException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeBeanAdapterTest {

    @Test
    void shouldRejectNestedGenericListInjection() throws Exception {
        UnsupportedInjectionException ex =
                assertThrows(
                        UnsupportedInjectionException.class,
                        () ->
                                new RuntimeBeanAdapter()
                                        .adaptComponent(NestedGenericComponent.class));

        assertTrue(ex.getMessage().contains("Nested generic type injection is not supported"));
        assertTrue(ex.getMessage().contains("List<"));
    }

    @Component
    public static class NestedGenericComponent {
        public NestedGenericComponent(List<Strategy<String>> strategies) {}
    }

    public interface Strategy<T> {}
}
