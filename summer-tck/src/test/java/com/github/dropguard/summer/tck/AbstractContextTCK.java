package com.github.dropguard.summer.tck;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.Testing;
import org.junit.jupiter.api.AfterEach;

/**
 * Base class for TCK tests that require a {@link BeanContainer}.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>Lazy context creation via {@link #context()}
 *   <li>Automatic cleanup in {@link AfterEach}
 *   <li>Default {@link #createContext()} producing a Runtime engine container
 * </ul>
 *
 * <p>Subclasses override {@link #createContext()} to select a different engine:
 *
 * <pre>
 * // AOT — full context
 * protected BeanContainer createContext() {
 * 	return TestContainerBuilder.buildAot();
 * }
 * </pre>
 *
 * <p>Usage:
 *
 * <pre>
 * // Simple case: Runtime engine (default)
 * public class RuntimeWebRouteTest extends AbstractWebRouteTCK {
 * }
 * </pre>
 */
public abstract class AbstractContextTCK extends AbstractTCK {

    protected BeanContainer context;

    /**
     * Create the BeanContainer for testing.
     *
     * <p>Default implementation builds a Runtime engine container. Subclasses may override to use
     * the AOT engine (e.g. {@code TestContainerBuilder.buildAot()}).
     */
    protected BeanContainer createContext() {
        return Testing.build();
    }

    /** Get the application context (lazy initialization). */
    protected BeanContainer context() {
        if (context == null) {
            context = createContext();
        }
        return context;
    }

    @AfterEach
    void cleanupContext() {
        closeQuietly(context);
        context = null;
    }
}
