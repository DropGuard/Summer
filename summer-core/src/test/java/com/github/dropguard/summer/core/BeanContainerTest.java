package com.github.dropguard.summer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        RouteInfo route = new RouteInfo("GET", "/x", "com.Ctrl", "get");
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

    @Test
    void buildSealsSealableBeansInReverseCreationOrder() {
        SealingBean.SEAL_ORDER.clear();
        BeanContainer.Builder builder = new BeanContainer.Builder();
        SealingBean first = new SealingBean("first");
        SealingBean second = new SealingBean("second");
        PlainBean plain = new PlainBean();
        builder.register(SealingBean.class, first);
        builder.register(SealingBean.class, second);
        builder.register(PlainBean.class, plain);
        BeanContainer container = builder.build();

        assertEquals(1, first.sealCount, "Sealable bean created first must be sealed");
        assertEquals(1, second.sealCount, "Sealable bean created second must be sealed");
        // Reverse creation order: the later-created bean is sealed first.
        assertEquals(List.of("second", "first"), SealingBean.SEAL_ORDER);
        // Non-Sealable beans are untouched and stay usable.
        assertEquals(plain, container.getBean(PlainBean.class));
    }

    @Test
    void sealedBeansRemainUsableAfterBuild() {
        BeanContainer.Builder builder = new BeanContainer.Builder();
        SealingBean bean = new SealingBean("bean");
        builder.register(SealingBean.class, bean);
        BeanContainer container = builder.build();

        assertEquals(1, bean.sealCount, "Sealable bean must be sealed at build");
        // Sealing stops assembly-time writes; the bean is still a registered, usable instance.
        assertEquals(bean, container.getBean(SealingBean.class));
    }

    /** Records seal order; also used to verify non-Sealable beans are untouched. */
    static final class SealingBean implements Sealable {
        static final java.util.List<String> SEAL_ORDER = new java.util.ArrayList<>();
        private final String name;
        int sealCount;

        SealingBean(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }

        @Override
        public void seal() {
            sealCount++;
            SEAL_ORDER.add(name);
        }
    }

    static final class PlainBean {}

    // --- Collection-injection identity contract (S-08): the container owns
    // BEANS, not type keys — two distinct instances that happen to be equals()
    // -equal are still two beans and must both reach collection injection. ---

    interface Named {}

    interface Titled {}

    /** Value semantics ON PURPOSE: distinct instances, equal by field. */
    static final class ValueBean implements Named, Titled {
        private final String v;

        ValueBean(String v) {
            this.v = v;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ValueBean other && other.v.equals(v);
        }

        @Override
        public int hashCode() {
            return v.hashCode();
        }
    }

    @Test
    void collectionInjectionKeepsDistinctButEqualBeans() {
        ValueBean first = new ValueBean("same");
        ValueBean second = new ValueBean("same");
        assert first != second;

        BeanContainer.Builder builder = new BeanContainer.Builder();
        builder.register(Named.class, first);
        builder.register(Titled.class, second);
        BeanContainer container = builder.build();

        List<ValueBean> all = container.getBeans(ValueBean.class);
        assertEquals(
                2,
                all.size(),
                "equals-equal but distinct beans are two beans — equals-based "
                        + "deduplication silently drops one from collection injection");
        assertTrue(all.contains(first));
        assertTrue(all.contains(second));
    }
}
