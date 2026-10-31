package com.github.dropguard.summer.tck.invisible.fixtures.startup;

import com.github.dropguard.summer.core.Component;

/**
 * Fails at INSTANTIATION with an Error, not an Exception — the shape {@code catch (Exception)} let
 * straight past: a constructor surfacing {@code ExceptionInInitializerError} / {@code
 * NoClassDefFoundError} / an assertion failure is still a constructor failure, and the beans built
 * before it must still be swept. (A throwing static initializer cannot simulate this: the route
 * scanner's discovery-time {@code Class.forName} initializes scanned classes before any bean is
 * instantiated, so the failure would fire before there is anything to sweep.)
 */
@Component
public class BrokenStaticInitBean {

    private final LeakyConnectionPool pool;

    public BrokenStaticInitBean(LeakyConnectionPool pool) {
        this.pool = pool;
        throw new AssertionError("boom during construction");
    }
}
