package com.github.dropguard.summer.grpc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.fixtures.GrpcTestConfig;
import com.github.dropguard.summer.test.annotation.SummerTest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status.Code;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Locks the {@link GrpcExceptionInterceptor} contract: a service handler throwing from {@code
 * onHalfClose} (unary) is translated into a gRPC {@code Status} instead of leaving the client
 * hanging. Also pins the streaming behavior ({@code onMessage}) so a future change to the
 * interceptor is forced to keep both paths in mind.
 */
@SummerTest
class GrpcExceptionInterceptorTest {

    private final GrpcServerRunner runner;
    private ManagedChannel channel;

    GrpcExceptionInterceptorTest(GrpcServerRunner runner) {
        this.runner = runner;
    }

    @AfterEach
    void tearDown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
    }

    @Test
    void unaryHandlerExceptionBecomesStatus() throws Exception {
        channel =
                ManagedChannelBuilder.forAddress("localhost", runner.getPort())
                        .usePlaintext()
                        .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        io.grpc.stub.ClientCalls.asyncUnaryCall(
                channel.newCall(GrpcTestConfig.UNARY_THROWS_METHOD, io.grpc.CallOptions.DEFAULT),
                "ping",
                new StreamObserver<String>() {
                    @Override
                    public void onNext(String value) {}

                    @Override
                    public void onError(Throwable t) {
                        error.set(t);
                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "client must receive a terminal status");
        assertNotNull(error.get(), "unary handler exception must surface as an error, not hang");
        assertTrue(
                io.grpc.Status.fromThrowable(error.get()).getCode() != Code.OK,
                "expected a non-OK status for a throwing unary handler");
    }

    @Test
    void streamingHandlerExceptionFromOnMessage() throws Exception {
        channel =
                ManagedChannelBuilder.forAddress("localhost", runner.getPort())
                        .usePlaintext()
                        .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        StreamObserver<String> requestObserver =
                io.grpc.stub.ClientCalls.asyncClientStreamingCall(
                        channel.newCall(
                                GrpcTestConfig.STREAMING_THROWS_METHOD,
                                io.grpc.CallOptions.DEFAULT),
                        new StreamObserver<String>() {
                            @Override
                            public void onNext(String value) {}

                            @Override
                            public void onError(Throwable t) {
                                error.set(t);
                                latch.countDown();
                            }

                            @Override
                            public void onCompleted() {
                                latch.countDown();
                            }
                        });

        // Send one message to trigger the handler's onMessage, then half-close.
        requestObserver.onNext("trigger");
        requestObserver.onCompleted();

        assertTrue(
                latch.await(5, TimeUnit.SECONDS),
                "streaming client must receive a terminal status");
        // The audit claim: onMessage exceptions "run naked" — the interceptor only guards
        // onHalfClose. Pin the ACTUAL behavior here so a fix is driven by a failing test.
        assertEquals(
                Code.INTERNAL,
                io.grpc.Status.fromThrowable(error.get()).getCode(),
                "streaming onMessage exception must be translated to a status, not left naked");
    }
}
