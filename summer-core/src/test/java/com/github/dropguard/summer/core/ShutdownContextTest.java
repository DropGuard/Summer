package com.github.dropguard.summer.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for {@link ShutdownContext} to ensure it properly handles exceptions and errors during
 * shutdown.
 */
class ShutdownContextTest {

    private static final Logger log = LoggerFactory.getLogger(ShutdownContextTest.class);

    @Test
    void shutdownContextContinuesAfterError() {
        ShutdownContext ctx = ShutdownContext.create();
        AtomicInteger taskCounter = new AtomicInteger(0);

        // Add a task that throws an Error
        ctx.addShutdownTask(
                () -> {
                    taskCounter.incrementAndGet();
                    throw new OutOfMemoryError("Simulated OOM");
                });

        // Add a task that should still run after the Error
        ctx.addShutdownTask(
                () -> {
                    taskCounter.incrementAndGet();
                    // This task should execute despite the previous Error
                });

        // This should not throw and should allow both tasks to run (the Error is caught and logged)
        assertDoesNotThrow(() -> ctx.runAll());

        // Both tasks should have executed
        assertTrue(taskCounter.get() >= 2, "Both tasks should have executed despite the Error");
    }

    @Test
    void shutdownContextContinuesAfterException() {
        ShutdownContext ctx = ShutdownContext.create();
        AtomicInteger taskCounter = new AtomicInteger(0);

        // Add a task that throws an Exception
        ctx.addShutdownTask(
                () -> {
                    taskCounter.incrementAndGet();
                    throw new IllegalArgumentException("Simulated exception");
                });

        // Add a task that should still run after the Exception
        ctx.addShutdownTask(
                () -> {
                    taskCounter.incrementAndGet();
                    // This task should execute despite the previous Exception
                });

        // This should not throw and should allow both tasks to run
        assertDoesNotThrow(() -> ctx.runAll());

        // Both tasks should have executed
        assertTrue(taskCounter.get() >= 2, "Both tasks should have executed despite the Exception");
    }

    @Test
    void shutdownContextLogsErrors() {
        ShutdownContext ctx = ShutdownContext.create();

        // Add a task that throws an Error
        ctx.addShutdownTask(
                () -> {
                    throw new OutOfMemoryError("Simulated OOM for logging test");
                });

        // This should not throw (Error is caught)
        assertDoesNotThrow(() -> ctx.runAll());

        // Note: Verifying log output would require a log capture mechanism,
        // but at least we verify it doesn't crash
    }
}
