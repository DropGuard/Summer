package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.tck.invisible.fixtures.startup.BrokenStaticInitBean;
import com.github.dropguard.summer.tck.invisible.fixtures.startup.LeakyConnectionPool;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A constructor that fails mid-assembly must not leak the beans already built: the abandoned-bean
 * sweep tears each built bean down EXACTLY ONCE per bean — the sweep used to iterate the type-key
 * map, so a bean registered under its concrete class AND an interface key was closed twice — with
 * the full per-bean rule ({@code @PreDestroy} then {@code close()}). Pinned on BOTH engines: the
 * AOT engine's generated build wraps its body in a try/catch(Throwable) sweep, the runtime's
 * assembly-failure catch calls the same shared sweep (see WireMethodGenerator / RuntimeContainer).
 */
class AbandonedBeanCleanupTest {

    @ParameterizedTest
    @EnumSource(Engine.class)
    void constructorFailureSweepsAbandonedBeansOnBothEngines(Engine engine) {
        LeakyConnectionPool.closeCount = 0;
        LeakyConnectionPool.EVENTS.clear();

        Throwable failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        Throwable.class,
                        () ->
                                TestContainer.builder()
                                        .engine(engine)
                                        .beans(
                                                LeakyConnectionPool.class,
                                                BrokenStaticInitBean.class)
                                        .build());

        assertTrue(
                hasInChain(failure, AssertionError.class),
                "the constructor's Error (wrapped by the instantiator's "
                        + "InvocationTargetException handling) must be the failure's cause");
        assertEquals(
                1,
                LeakyConnectionPool.closeCount,
                "the pool built before the failure must be closed exactly once — per bean, "
                        + "not once per type key (concrete class + AutoCloseable). Pre-fix this "
                        + "was 2 (double close); without any sweep it is 0.");
        assertEquals(
                java.util.List.of("preDestroy", "close"),
                LeakyConnectionPool.EVENTS,
                "the abandoned sweep must run the full per-bean teardown rule, in order");
    }

    private static boolean hasInChain(Throwable t, Class<? extends Throwable> type) {
        for (Throwable cur = t; cur != null; cur = cur.getCause() == cur ? null : cur.getCause()) {
            if (type.isInstance(cur)) {
                return true;
            }
        }
        return false;
    }
}
