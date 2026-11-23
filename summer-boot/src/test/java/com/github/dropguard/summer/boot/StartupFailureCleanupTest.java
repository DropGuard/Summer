package com.github.dropguard.summer.boot;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.ApplicationRunner;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.config.ShutdownConfig;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * A failed start closes the container before the failure propagates, and that cleanup is bounded by
 * the shutdown budget: a bean stuck in {@code close()} cannot hang the failing process.
 */
class StartupFailureCleanupTest {

    /** A bean whose close() is observable (and optionally blocks until released). */
    static final class Tracked implements AutoCloseable {
        final AtomicBoolean closed = new AtomicBoolean();
        final CountDownLatch release;

        Tracked(CountDownLatch release) {
            this.release = release;
        }

        @Override
        public void close() throws InterruptedException {
            closed.set(true);
            if (release != null) release.await();
        }
    }

    private static BeanContainer container(
            long timeoutMs, Tracked tracked, ApplicationRunner runner) {
        BeanContainer.Builder builder = new BeanContainer.Builder();
        ShutdownConfig shutdown = () -> timeoutMs;
        builder.register(ShutdownConfig.class, shutdown);
        builder.register(Tracked.class, tracked);
        builder.register(ApplicationRunner.class, runner);
        return builder.build(Engine.RUNTIME);
    }

    @Test
    void failingRunnerClosesTheContainerAndRethrowsTheOriginalFailure() {
        Tracked tracked = new Tracked(null);
        IllegalStateException boom = new IllegalStateException("runner failed");
        BeanContainer context =
                container(
                        1_000L,
                        tracked,
                        ctx -> {
                            throw boom;
                        });

        Exception thrown =
                assertThrows(Exception.class, () -> SummerApplication.startContainer(context));

        assertSame(boom, thrown, "the original startup failure propagates unchanged");
        assertTrue(tracked.closed.get(), "the half-started container is closed");
        assertTrue(context.isShuttingDown(), "the container reads as closed");
    }

    @Test
    void stuckCloseIsAbandonedAfterTheShutdownBudget() {
        CountDownLatch release = new CountDownLatch(1);
        Tracked tracked = new Tracked(release);
        IllegalStateException boom = new IllegalStateException("runner failed");
        BeanContainer context =
                container(
                        200L,
                        tracked,
                        ctx -> {
                            throw boom;
                        });
        try {
            Exception thrown =
                    assertTimeoutPreemptively(
                            Duration.ofSeconds(5),
                            () ->
                                    assertThrows(
                                            Exception.class,
                                            () -> SummerApplication.startContainer(context)));

            assertSame(boom, thrown, "the original startup failure propagates unchanged");
            assertTrue(tracked.closed.get(), "cleanup was attempted");
            assertTrue(
                    Arrays.stream(boom.getSuppressed())
                            .anyMatch(s -> s instanceof TimeoutException),
                    "the abandoned cleanup is reported as a suppressed TimeoutException");
        } finally {
            release.countDown();
        }
    }
}
