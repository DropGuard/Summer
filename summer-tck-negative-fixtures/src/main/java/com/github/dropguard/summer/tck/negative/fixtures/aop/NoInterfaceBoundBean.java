package com.github.dropguard.summer.tck.negative.fixtures.aop;

import com.github.dropguard.summer.core.Component;

/**
 * Negative fixture: a bean annotated with an {@code @InterceptorBinding} but implementing <em>no
 * interface</em>.
 *
 * <p>Summer's AOP model uses JDK dynamic proxies, which require the target to implement at least
 * one interface. This fixture lets the TCK assert that such a misconfiguration fails fast at
 * assembly (throwing {@code SummerAopException} with {@code ErrorCode.AOP_NO_INTERFACE}) instead of
 * silently skipping the proxy and leaving the binding dead.
 */
@Component
public class NoInterfaceBoundBean {

    @AopMarker
    public void doWork() {
        // intentionally empty: the test only asserts assembly is rejected
    }
}
