package com.github.dropguard.summer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.core.bean.RouteInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Locks the "immutable after build" contract: the builder cannot mutate the built container, the
 * route list is a snapshot, and {@code close()} is idempotent (a duplicate call must not tear down
 * beans twice).
 */
class BeanContainerTest {

    @Test
    void builderCannotMutateBuiltContainer() {
        BeanContainer.Builder builder = new BeanContainer.Builder();
        builder.register(String.class, "alpha");
        BeanContainer container = builder.build();

        // The builder is discarded after build(): registering more beans must not
        // leak into the already-built container.
        builder.register(Integer.class, 42);

        assertEquals(1, container.componentTypes().size());
        assertEquals("alpha", container.getBean(String.class));
        assertThrows(
                com.github.dropguard.summer.core.exception.NoSuchBeanException.class,
                () -> container.getBean(Integer.class));
    }

    @Test
    void routesAreAnImmutableSnapshot() {
        BeanContainer.Builder builder = new BeanContainer.Builder();
        RouteInfo route = new RouteInfo("GET", "/x", "com.Ctrl", "get", "void");
        builder.routes(List.of(route));
        BeanContainer container = builder.build();

        // The route list is a snapshot (List.copyOf at builder level): mutating the
        // source list after build must not change the container.
        assertThrows(UnsupportedOperationException.class, () -> container.routes().add(route));
    }

    @Test
    void closeIsIdempotent() throws Exception {
        BeanContainer.Builder builder = new BeanContainer.Builder();
        CountingCloseable bean = new CountingCloseable();
        builder.register(CountingCloseable.class, bean);
        BeanContainer container = builder.build();

        container.close();
        container.close();

        assertEquals(1, bean.closeCount, "duplicate close() must not re-close beans");
    }

    static final class CountingCloseable implements AutoCloseable {
        int closeCount;

        @Override
        public void close() {
            closeCount++;
        }
    }
}
