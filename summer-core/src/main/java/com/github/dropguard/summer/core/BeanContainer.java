package com.github.dropguard.summer.core;

import com.github.dropguard.summer.core.annotation.Order;
import com.github.dropguard.summer.core.bean.RouteInfo;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable bean container that serves as the DI engine's output.
 *
 * <p>Use {@link Builder} during the construction phase to register beans, then call {@link
 * Builder#build()} to produce an immutable container. The builder is discarded after that — no
 * further mutation is possible.
 */
public final class BeanContainer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BeanContainer.class);

    /**
     * One bean as the container owns it: the unique instance plus the lifecycle metadata. The CDI
     * model — the container holds beans (identified instances), not type keys; lookups resolve via
     * the type index. A {@link BeanEntry} with {@code isProduct} true was created by a
     * {@code @Bean} producer method and is closed before its producer (the product's close may
     * access the producer's still-alive state).
     */
    record BeanEntry(Object instance, boolean isProduct) {}

    private final Map<Class<?>, Object> typeIndex;
    private final List<BeanEntry> beans;
    private final Engine engine;
    private final List<RouteInfo> routes;
    private final ShutdownContext shutdownContext = ShutdownContext.create();

    /** Guards {@link #close()} so a duplicate call is a no-op (idempotent teardown). */
    private boolean closed;

    private BeanContainer(
            Map<Class<?>, Object> typeIndex,
            List<BeanEntry> beans,
            Engine engine,
            List<RouteInfo> routes) {
        this.typeIndex = Collections.unmodifiableMap(new HashMap<>(typeIndex));
        this.beans = List.copyOf(beans);
        this.engine = engine;
        this.routes = routes;
    }

    /** Returns which engine produced this container. */
    public Engine engine() {
        return engine;
    }

    /** Returns all type keys registered in the container (interfaces included). */
    public Set<Class<?>> componentTypes() {
        return typeIndex.keySet();
    }

    /** Returns route metadata collected during container construction. */
    public List<RouteInfo> routes() {
        return routes;
    }

    // ---- Read-only bean lookup ----

    /**
     * Gets a bean by type. First tries exact key match, then falls back to {@code isInstance}
     * scanning. Throws if zero or multiple matches.
     */
    public <T> T getBean(Class<T> type) {
        return getBean(typeIndex, type);
    }

    /** Returns all beans whose type is assignable to the given type, sorted by {@code @Order}. */
    public <T> List<T> getBeans(Class<T> type) {
        return getBeans(typeIndex, type);
    }

    // Shared lookup core for BeanContainer and Builder: the two operate on different maps (the
    // frozen container map vs the builder's mutable pre-build map), so the map is passed in —
    // the lookup logic itself is single-sourced here to prevent drift between the two views.
    @SuppressWarnings("unchecked")
    private static <T> T getBean(Map<Class<?>, Object> singletons, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("getBean requires a non-null type");
        }
        Object exact = singletons.get(type);
        if (exact != null) {
            return (T) exact;
        }
        List<T> matches = getBeans(singletons, type);
        if (matches.isEmpty()) {
            throw new NoSuchBeanException("No bean found of type: " + type.getName());
        }
        if (matches.size() > 1) {
            throw new AmbiguousBeanException("Multiple beans found for type: " + type.getName());
        }
        return matches.get(0);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> getBeans(Map<Class<?>, Object> singletons, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("getBeans requires a non-null type");
        }
        List<T> result = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (Object bean : singletons.values()) {
            if (type.isInstance(bean) && seen.add(bean)) {
                result.add((T) bean);
            }
        }
        result.sort(ORDER_COMPARATOR);
        return result;
    }

    /**
     * Resolves the {@code @Order} value for a bean. Checks the concrete class first, then its
     * interfaces (JDK proxies report their proxy class, not the original).
     */
    static int orderOf(Object bean) {
        Class<?> cls = bean.getClass();
        Order order = cls.getAnnotation(Order.class);
        if (order != null) return order.value();
        for (Class<?> iface : cls.getInterfaces()) {
            order = iface.getAnnotation(Order.class);
            if (order != null) return order.value();
        }
        return Integer.MAX_VALUE;
    }

    private static final Comparator<Object> ORDER_COMPARATOR =
            Comparator.comparingInt(BeanContainer::orderOf);

    /** Checks whether a bean of the given type is registered (exact key or assignable). */
    public boolean containsBean(Class<?> type) {
        if (typeIndex.containsKey(type)) {
            return true;
        }
        for (Object bean : typeIndex.values()) {
            if (type.isInstance(bean)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws Exception {
        synchronized (this) {
            if (closed) {
                log.debug("[Summer] BeanContainer already closed — ignoring duplicate close()");
                return;
            }
            closed = true;
        }
        // Phase 1: input drivers (servers) tear down first, each via its own
        // registered task (stop accepting, drain in-flight, release resources),
        // in reverse registration order — so external traffic stops before
        // anything underneath is torn down.
        shutdownContext.runAll();

        // Phase 2: @Bean-produced instances close first (their close may access the producer's
        // still-alive state — the CDI producer-destruction rule), then the remaining beans. Each
        // group closes in REVERSE creation order: creation is dependency-first (a bean's
        // constructor parameters are built before it), so reversing it closes dependents before
        // their dependencies — a repository that flushes on close still finds its connection pool
        // alive. The container owns each bean ONCE (the CDI model: beans, not type keys), so no
        // instance is closed more than once regardless of how many type keys it is registered
        // under. There is no topological teardown guarantee beyond this: the CDI does not provide
        // one, and order-sensitive teardown goes through ShutdownContext or the bean's own cleanup.
        for (int i = beans.size() - 1; i >= 0; i--) {
            BeanEntry entry = beans.get(i);
            if (entry.isProduct() && entry.instance() instanceof AutoCloseable ac) {
                closeQuietly(ac);
            }
        }
        for (int i = beans.size() - 1; i >= 0; i--) {
            BeanEntry entry = beans.get(i);
            if (!entry.isProduct() && entry.instance() instanceof AutoCloseable ac) {
                closeQuietly(ac);
            }
        }
    }

    private static void closeQuietly(AutoCloseable ac) {
        try {
            ac.close();
        } catch (Exception e) {
            log.error("[Summer] Error closing bean {}: {}", ac.getClass().getName(), e);
        }
    }

    /**
     * Registers a shutdown task with the container's {@link ShutdownContext}. Tasks run in reverse
     * registration order on {@link #close()}, before the remaining {@link AutoCloseable} beans are
     * closed. Input drivers (servers) call this from their startup callback to encapsulate their
     * own teardown staging.
     */
    public void addShutdownTask(Runnable task) {
        shutdownContext.addShutdownTask(task);
    }

    // ---- Builder ----

    /**
     * Mutable builder for {@link BeanContainer}. Used during the context construction phase to
     * register beans and peek at already-registered beans.
     */
    public static final class Builder {

        private final Map<Class<?>, Object> typeIndex = new HashMap<>();
        private final List<BeanEntry> beans = new ArrayList<>();
        // Identity-based: the same instance registered under its class AND its interfaces must be
        // owned once by the container (the CDI model — the container holds beans, not type keys).
        private final Set<Object> seen =
                Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private List<RouteInfo> routes = List.of();

        /** Registers a bean instance under the given type key. */
        public void register(Class<?> type, Object instance) {
            register(type, instance, false);
        }

        /**
         * Registers a {@code @Bean}-produced instance. Its teardown is tied to its producer: the
         * container closes every product before any non-product, so the product's {@code close()}
         * can still access the producer's alive state (the CDI producer-destruction rule).
         */
        public void registerProduct(Class<?> type, Object instance) {
            register(type, instance, true);
        }

        private void register(Class<?> type, Object instance, boolean isProduct) {
            typeIndex.put(type, instance);
            if (seen.add(instance)) {
                beans.add(new BeanEntry(instance, isProduct));
            }
        }

        /** Sets route metadata collected during container construction. */
        public void routes(List<RouteInfo> routes) {
            this.routes = List.copyOf(routes);
        }

        /**
         * Returns the instance registered under the exact type key, or {@code null} if not present.
         * Does not perform assignability matching.
         */
        public Object peek(Class<?> type) {
            return typeIndex.get(type);
        }

        /**
         * Gets a bean by type. First tries exact key match, then falls back to {@code isInstance}
         * scanning. Throws if zero or multiple matches.
         */
        public <T> T getBean(Class<T> type) {
            // Qualified call: the Builder's own getBean(Class) shadows the shared static.
            return BeanContainer.getBean(typeIndex, type);
        }

        /**
         * Returns all beans whose type is assignable to the given type, sorted by {@code @Order}.
         */
        public <T> List<T> getBeans(Class<T> type) {
            return BeanContainer.getBeans(typeIndex, type);
        }

        /** Returns a read-only view of all registered type keys. */
        public Map<Class<?>, Object> singletons() {
            return Collections.unmodifiableMap(typeIndex);
        }

        /**
         * Removes the bean registered under the exact type key. Returns the removed instance, or
         * null.
         */
        public Object remove(Class<?> type) {
            Object removed = typeIndex.remove(type);
            // Identity scan: the DI registry keys bean instances by identity (a value object with
            // an overridden equals must not read as "still registered" through containsValue).
            if (removed != null && !typeIndex.values().stream().anyMatch(v -> v == removed)) {
                beans.removeIf(e -> e.instance() == removed);
                seen.remove(removed);
            }
            return removed;
        }

        /** Produces an immutable {@link BeanContainer}. */
        public BeanContainer build(Engine engine) {
            // The container constructor defensively copies both the type index and the bean list,
            // so a builder still referenced after build() can no longer mutate the built container.
            return new BeanContainer(typeIndex, beans, engine, routes);
        }

        /** Convenience overload — defaults to {@link Engine#RUNTIME}. */
        public BeanContainer build() {
            return build(Engine.RUNTIME);
        }
    }
}
