package com.github.dropguard.summer.tck.grpc;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.grpc.client.GrpcChannelManager;
import com.github.dropguard.summer.grpc.server.GrpcServerRunner;
import com.github.dropguard.summer.tck.AbstractTCK;
import com.github.dropguard.summer.test.annotation.DualEngine;
import org.junit.jupiter.api.AfterEach;

/**
 * TCK for gRPC component discovery and basic behavior.
 *
 * <p>Verifies that both DI engines correctly discover and wire gRPC infrastructure beans without
 * requiring an actual gRPC server. The container is supplied by the subclass constructor (the
 * {@code @SummerTest} injection contract) — this base class no longer builds its own context, so
 * subclasses run identically on Runtime and AOT via {@link DualEngine}.
 */
public abstract class AbstractGrpcTCK extends AbstractTCK {

    protected final BeanContainer context;

    protected AbstractGrpcTCK(BeanContainer context) {
        this.context = context;
    }

    @AfterEach
    void cleanupContext() {
        // The injected context is the RUNTIME instance's container; the AOT invocation's manager
        // (from the method-parameter container) is closed when its container shuts down at the
        // suite end — the channel's lifetime there is bounded by the universe's, which is fine.
        GrpcChannelManager mgr = context.getBean(GrpcChannelManager.class);
        try {
            mgr.close();
        } catch (Exception ignored) {
            // Cleanup failure is non-critical in tests
        }
    }

    @DualEngine
    void testChannelManagerCachesPerTarget(BeanContainer container) {
        // The invocation's own container: the AOT invocation must exercise the AOT container's
        // wiring,
        // not the RUNTIME instance's (the instance is always built from the RUNTIME container).
        GrpcChannelManager mgr = container.getBean(GrpcChannelManager.class);
        var ch1 = mgr.getChannel("localhost:9999");
        var ch2 = mgr.getChannel("localhost:9999");
        var ch3 = mgr.getChannel("localhost:8888");

        assertSame(ch1, ch2, "Same target should return same channel");
        assertNotSame(ch1, ch3, "Different target should return different channel");
        assertFalse(ch1.isShutdown());
    }

    @DualEngine
    void testServerRunnerDoesNotStartWithoutServices(BeanContainer container) {
        GrpcServerRunner runner = container.getBean(GrpcServerRunner.class);
        assertNotNull(runner);
        assertEquals(
                -1, runner.getPort(), "Port should be -1 when no gRPC services are registered");
    }
}
