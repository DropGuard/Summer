package summer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Immutable bean container that serves as the DI engine's output.
 */
public final class BeanContainer implements AutoCloseable {

    private final BeanRegistry registry;
    private final Engine engine;

    private BeanContainer(BeanRegistry registry, Engine engine) {
        this.registry = registry;
        this.engine = engine;
    }

    public static BeanContainer create(BeanRegistry registry, Engine engine) {
        return new BeanContainer(registry, engine);
    }

    public static BeanContainer create(BeanRegistry registry) {
        return create(registry, Engine.RUNTIME);
    }

    /** Returns which engine produced this container. */
    public Engine engine() {
        return engine;
    }

    /** Returns all type keys registered in the container (interfaces included). */
    public Set<Class<?>> componentTypes() {
        return registry.singletons().keySet();
    }

    // ---- BeanContainer ----

    public <T> T getBean(Class<T> type) {
        return registry.getBean(type);
    }

    public <T> List<T> getBeans(Class<T> type) {
        return registry.getBeans(type);
    }

    public void close() throws Exception {
        List<Object> reversed = new ArrayList<>(registry.singletons().values());
        Collections.reverse(reversed);
        for (Object bean : reversed) {
            if (bean instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) bean).close();
                } catch (Exception e) {
                    System.err.println("[Summer] Error closing: " + e);
                }
            }
        }
    }
}