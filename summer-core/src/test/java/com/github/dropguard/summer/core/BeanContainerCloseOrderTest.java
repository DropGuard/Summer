package com.github.dropguard.summer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The container owns each bean ONCE (the CDI model — beans, not type keys) and closes products
 * before non-products (the producer-destruction rule: the product's close may access the producer's
 * alive state). No topological guarantee beyond that; order-sensitive teardown goes through
 * ShutdownContext.
 */
class BeanContainerCloseOrderTest {

    static final List<String> CLOSED = new ArrayList<>();

    record Closing(String name, Runnable onClose) implements AutoCloseable {
        @Override
        public void close() {
            CLOSED.add(name);
            if (onClose != null) onClose.run();
        }
    }

    @Test
    void productsCloseBeforeProducers() throws Exception {
        CLOSED.clear();
        BeanContainer.Builder builder = new BeanContainer.Builder();
        Closing producer = new Closing("producer", null);
        Closing product = new Closing("product", null);
        builder.register(Producer.class, producer);
        builder.registerProduct(Product.class, product);
        BeanContainer container = builder.build(Engine.RUNTIME);
        container.close();
        assertEquals(
                List.of("product", "producer"), CLOSED, "product must close before its producer");
    }

    @Test
    void instanceRegisteredUnderMultipleKeysClosesOnce() throws Exception {
        CLOSED.clear();
        BeanContainer.Builder builder = new BeanContainer.Builder();
        Closing bean = new Closing("bean", null);
        builder.register(Closing.class, bean);
        builder.register(AutoCloseable.class, bean); // the same instance under a second key
        builder.register(Object.class, bean); // and a third
        BeanContainer container = builder.build(Engine.RUNTIME);
        container.close();
        assertEquals(List.of("bean"), CLOSED, "the same instance must be closed exactly once");
    }

    @Test
    void closeIsIdempotent() throws Exception {
        CLOSED.clear();
        BeanContainer.Builder builder = new BeanContainer.Builder();
        Closing bean = new Closing("bean", null);
        builder.register(Closing.class, bean);
        BeanContainer container = builder.build(Engine.RUNTIME);
        container.close();
        container.close();
        assertEquals(List.of("bean"), CLOSED, "a second close() must be a no-op");
    }

    interface Producer {}

    interface Product {}

    /**
     * A dependency (created first) and its dependent (created after) — the dependent must close
     * first.
     */
    static final class Pool implements AutoCloseable {
        @Override
        public void close() {
            CLOSED.add("pool");
        }
    }

    static final class Repository implements AutoCloseable {
        Repository(Pool pool) {
            // Constructor injection: the Pool is created BEFORE this bean (dependency-first).
        }

        @Override
        public void close() {
            CLOSED.add("repo");
        }
    }

    @Test
    void dependentClosesBeforeItsDependency() throws Exception {
        CLOSED.clear();
        BeanContainer.Builder builder = new BeanContainer.Builder();
        builder.register(Pool.class, new Pool());
        builder.register(Repository.class, new Repository(new Pool()));
        BeanContainer container = builder.build(Engine.RUNTIME);
        container.close();
        assertEquals(
                List.of("repo", "pool"),
                CLOSED,
                "reverse creation order: the dependent (created later) closes before its"
                        + " dependency");
    }
}
