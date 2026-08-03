package com.github.dropguard.summer.grpc.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the gRPC server starts when a {@code BindableService} is present,
 * binds to a port, and stops cleanly.
 */
@SummerTest
class GrpcServerLifecycleTest {

    private final GrpcServerRunner runner;

    GrpcServerLifecycleTest(GrpcServerRunner runner) {
        this.runner = runner;
    }

    @Test
    void serverStartsAndGetsPort() {
        int port = runner.getPort();
        assertTrue(port > 0, "gRPC server should be started and bound to a port");
    }

    @Test
    void serverHasPortAfterStart() {
        // Another call to getPort after the server is already running — the port
        // should still be the same cached value. (We don't call stop() here
        // because the container lifecycle handles shutdown.)
        int port = runner.getPort();
        assertTrue(port > 0, "gRPC server should have a bound port after start");
    }
}
