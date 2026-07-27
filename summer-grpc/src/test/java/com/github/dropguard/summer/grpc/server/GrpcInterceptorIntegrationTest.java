package com.github.dropguard.summer.grpc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.fixtures.GrpcTestConfig;
import com.github.dropguard.summer.test.Testing;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCalls;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GrpcInterceptorIntegrationTest {

    @BeforeAll
    public static void setupProperty() {
        System.setProperty("com.github.dropguard.summer.grpc.port", "0");
    }

    @Test
    public void testGrpcServerInterceptorDiscovery() throws Exception {
        var ctx = Testing.build();

        GrpcServerRunner serverRunner = ctx.getBean(GrpcServerRunner.class);
        serverRunner.run(ctx);
        int port = serverRunner.getPort();
        assertTrue(port > 0, "Server should be bound to a valid port");

        ManagedChannel channel =
                ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();

        try {
            String response =
                    ClientCalls.blockingUnaryCall(
                            channel,
                            GrpcTestConfig.TEST_METHOD,
                            CallOptions.DEFAULT,
                            "RequestData");
            assertEquals("Hello Intercepted!", response);
        } finally {
            channel.shutdown();
            ctx.close();
        }
    }
}
