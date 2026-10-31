package com.github.dropguard.summer.tck.invisible.fixtures.aop;

/**
 * Negative fixture pair: the impl carries an {@code @AopMarker} binding while the interceptor that
 * provides it ({@code MarkerInterceptor}) is deliberately NOT seeded into the container. Lets the
 * TCK assert that an orphaned binding fails fast at assembly instead of silently running the bean
 * un-intercepted.
 */
public interface OrphanBoundService {

    void doWork();
}
