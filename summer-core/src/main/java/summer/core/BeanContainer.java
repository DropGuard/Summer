package summer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

/**
 * Immutable bean container that serves as the DI engine's output.
 *
 * <p>
 * Created by {@link #create(BeanRegistry, Engine)} after the registry has been
 * fully populated (bean discovery, condition evaluation, AOP proxy wrapping).
 * Both the reflection engine ({@code RuntimeApplicationContext}) and the AOT
 * engine ({@code summer-maven-plugin}) produce a {@code BeanContainer}.
 * </p>
 *
 * <p>
 * Implements {@link ApplicationContext} so existing code that consumes the
 * interface does not need to change.
 * </p>
 */
public final class BeanContainer implements ApplicationContext {

    private final BeanRegistry registry;
    private final Engine engine;

    private BeanContainer(BeanRegistry registry, Engine engine) {
        this.registry = registry;
        this.engine = engine;
    }

    /**
     * Creates a frozen container from a fully-wired registry.
     * {@code close()} walks all {@link AutoCloseable} beans in reverse
     * registration order.
     */
    public static BeanContainer create(BeanRegistry registry, Engine engine) {
        return new BeanContainer(registry, engine);
    }

    /**
     * Convenience overload for {@link #create(BeanRegistry, Engine)} that
     * defaults to {@link Engine#RUNTIME} (reflection engine).
     */
    public static BeanContainer create(BeanRegistry registry) {
        return create(registry, Engine.RUNTIME);
    }

    // ---- ApplicationContext implementation ----

    @Override
    public Engine engine() {
        return engine;
    }

    @Override
    public <T> T getBean(Class<T> type) {
        return registry.getBean(type);
    }

    @Override
    public <T> List<T> getBeans(Class<T> type) {
        return registry.getBeans(type);
    }

    @Override
    public Set<Class<?>> getRegisteredTypes() {
        return registry.getRegisteredTypes();
    }

    @Override
    public void close() throws Exception {
        List<Object> reversed = new ArrayList<>(registry.singletons().values());
        Collections.reverse(reversed);
        for (Object bean : reversed) {
            if (bean instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) bean).close();
                } catch (Exception e) {
                    // best-effort close
                    System.err.println("[Summer] Error closing: " + e);
                }
            }
        }
    }
}