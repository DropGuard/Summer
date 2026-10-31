package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.tck.invisible.fixtures.startup.BrokenStaticInitBean;
import com.github.dropguard.summer.tck.invisible.fixtures.startup.LeakyConnectionPool;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;

/**
 * A constructor that fails mid-assembly must not leak the beans already built: the abandoned-bean
 * sweep closes each built bean EXACTLY ONCE per bean — the sweep used to iterate the type-key map,
 * so a bean registered under its concrete class AND an interface key was closed twice. (The sweep's
 * Exception|Error guard is defensive: constructor failures reach it wrapped as
 * BeanCreationException by the instantiator's InvocationTargetException handling. The AOT engine
 * deliberately does not sweep — a failed AOT boot exits the process; see WireMethodGenerator.)
 */
class AbandonedBeanCleanupTest {

    @Test
    void constructorErrorSweepClosesAbandonedBeansExactlyOnce() {
        LeakyConnectionPool.closeCount = 0;

        Throwable failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        Throwable.class,
                        () ->
                                TestContainer.builder()
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
