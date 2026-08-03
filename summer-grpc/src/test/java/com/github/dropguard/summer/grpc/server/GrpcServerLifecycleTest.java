package com.github.dropguard.summer.grpc.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.Test;

@SummerTest
class GrpcServerLifecycleTest {

    private final GrpcServerRunner runner;

    GrpcServerLifecycleTest(GrpcServerRunner runner) {
        this.runner = runner;
    }

    @Test
    void serverBindsAndReportsPort() {
        assertTrue(runner.getPort() > 0, "gRPC server must bind to a port");
    }
}
