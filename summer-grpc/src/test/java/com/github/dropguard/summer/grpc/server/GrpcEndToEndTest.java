package com.github.dropguard.summer.grpc.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.fixtures.GrpcTestConfig;
import com.github.dropguard.summer.test.annotation.SummerTest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Canonical end-to-end test: a real gRPC client calls a service running on the Summer-managed
 * server. Proves the full pipeline — server start, interceptor, handler, response — works in one
 * place.
 */
@SummerTest
class GrpcEndToEndTest {

    private final GrpcServerRunner runner;
    private ManagedChannel channel;

    GrpcEndToEndTest(GrpcServerRunner runner) {
        this.runner = runner;
    }

    @AfterEach
    void tearDown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
    }

    @Test
    void unaryCallReturnsInterceptedResponse() throws Exception {
        channel =
                ManagedChannelBuilder.forAddress("localhost", runner.getPort())
                        .usePlaintext()
                        .build();

        var latch = new CountDownLatch(1);
        var received = new String[1];
        io.grpc.stub.ClientCalls.asyncUnaryCall(
                channel.newCall(GrpcTestConfig.TEST_METHOD, io.grpc.CallOptions.DEFAULT),
                "ping",
                new StreamObserver<String>() {
                    @Override
                    public void onNext(String value) {
                        received[0] = value;
                    }

                    @Override
                    public void onError(Throwable t) {
                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(received[0]);
        assertTrue(received[0].startsWith("Hello "));
    }
}
