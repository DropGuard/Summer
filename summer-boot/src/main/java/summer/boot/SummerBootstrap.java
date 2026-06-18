package summer.boot;

import summer.core.BeanContainer;

/**
 * Bootstrap interface for AOT-generated DI containers.
 *
 * <p>
 * The AOT build plugin generates an implementation of this interface at
 * compile time. {@code SummerApplication} discovers it via
 * {@code Class.forName} and calls {@link #launch()} to obtain a fully-wired
 * {@link BeanContainer}.
 * </p>
 */
@FunctionalInterface
public interface SummerBootstrap {

    /**
     * Creates and returns a fully-wired {@link BeanContainer} with all beans
     * instantiated via the AOT code path.
     */
    BeanContainer launch() throws Exception;
}