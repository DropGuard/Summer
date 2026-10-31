package com.github.dropguard.summer.tck.invisible.fixtures.aop;

import com.github.dropguard.summer.core.Component;

/**
 * The orphaned half of the pair: bound with {@code @AopMarker}, but the TCK seeds this bean without
 * {@code MarkerInterceptor}, so no registered interceptor provides the binding.
 */
@Component
public class OrphanBoundServiceImpl implements OrphanBoundService {

    @AopMarker
    @Override
    public void doWork() {
        // intentionally empty: the test only asserts assembly is rejected
    }
}
