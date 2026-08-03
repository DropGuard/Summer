package com.github.dropguard.summer.grpc.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.fixtures.GrpcTestConfig;
import com.github.dropguard.summer.test.annotation.SummerTest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies gRPC server-streaming by calling the test service directly through
 * a {@link ManagedChannel} connected to the running server.
 */
@SummerTest
class GrpcServerStreamingTest {

    private final GrpcServerRunner runner;
    private ManagedChannel channel;

    GrpcServerStreamingTest(GrpcServerRunner runner) {
        this.runner = runner;
    }

    @AfterEach
    void tearDown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
    }

    @Test
    void serverStreamingSendsMultipleMessages() throws Exception {
        channel = ManagedChannelBuilder.forAddress("localhost", runner.getPort())
                .usePlaintext()
                .build();

        List<String> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // The test interceptor overwrites x-test-interceptor to "Intercepted!",
        // which can't be parsed as int → defaults to 3 chunks.
        io.grpc.stub.ClientCalls.asyncServerStreamingCall(
                channel.newCall(GrpcTestConfig.STREAM_METHOD, io.grpc.CallOptions.DEFAULT),
                "start",
                new StreamObserver<String>() {
                    @Override
                    public void onNext(String value) {
                        received.add(value);
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

        assertTrue(latch.await(5, TimeUnit.SECONDS), "stream should complete within timeout");
        assertTrue(received.size() >= 1, "should receive at least one chunk from server streaming");
        assertTrue(received.get(0).startsWith("Chunk "));
    }
}
