package summer.tck.grpc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.grpc.client.GrpcChannelManager;
import summer.grpc.server.GrpcServerRunner;
import summer.tck.AbstractTCK;
import summer.test.annotation.DualEngine;

/**
 * TCK for gRPC component discovery and basic behavior.
 *
 * <p>
 * Verifies that both DI engines correctly discover and wire gRPC infrastructure
 * beans without requiring an actual gRPC server. The container is supplied by the
 * subclass constructor (the {@code @SummerTest} injection contract) — this base
 * class no longer builds its own context, so subclasses run identically on
 * Runtime and AOT via {@link DualEngine}.
 * </p>
 */
public abstract class AbstractGrpcTCK extends AbstractTCK {

	protected final BeanContainer context;

	protected AbstractGrpcTCK(BeanContainer context) {
		this.context = context;
	}

	@AfterEach
	void cleanupContext() {
		GrpcChannelManager mgr = context.getBean(GrpcChannelManager.class);
		try {
			mgr.close();
		} catch (Exception ignored) {
			// Cleanup failure is non-critical in tests
		}
	}

	@DualEngine
	@Test
	void testChannelManagerCachesPerTarget() {
		GrpcChannelManager mgr = context.getBean(GrpcChannelManager.class);
		var ch1 = mgr.getChannel("localhost:9999");
		var ch2 = mgr.getChannel("localhost:9999");
		var ch3 = mgr.getChannel("localhost:8888");

		assertSame(ch1, ch2, "Same target should return same channel");
		assertNotSame(ch1, ch3, "Different target should return different channel");
		assertFalse(ch1.isShutdown());
	}

	@DualEngine
	@Test
	void testServerRunnerDoesNotStartWithoutServices() {
		GrpcServerRunner runner = context.getBean(GrpcServerRunner.class);
		assertNotNull(runner);
		assertEquals(-1, runner.getPort(), "Port should be -1 when no gRPC services are registered");
	}
}
