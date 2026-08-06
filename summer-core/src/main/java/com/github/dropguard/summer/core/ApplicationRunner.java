package com.github.dropguard.summer.core;

/**
 * Interface used to indicate that a bean should run after the container is built.
 *
 * <p>This is primarily used by servers (HTTP, gRPC) to hook into the startup lifecycle after the
 * container has been fully assembled.
 */
public interface ApplicationRunner {

    /**
     * Start this runner. Called after all beans are instantiated and the container is ready.
     *
     * @param container the built application container
     * @throws Exception if startup fails
     */
    void run(BeanContainer context) throws Exception;
}
