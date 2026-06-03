package summer.tck.grpc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.grpc.client.GrpcChannelManager;
import summer.grpc.server.GrpcServerRunner;

/**
 * TCK test for gRPC component discovery and basic behavior.
 *
 * <p>
 * Verifies that both DI engines correctly discover and wire gRPC components
 * without requiring an actual gRPC server.
 * </p>
 */
public abstract class AbstractGrpcTCK {

	protected ApplicationContext context;

	protected abstract ApplicationContext createContext(Class<?>... components);

	@AfterEach
	void tearDown() {
		if (context != null) {
			GrpcChannelManager mgr = context.getBean(GrpcChannelManager.class);
			try {
				mgr.close();
			} catch (Exception ignored) {
				// Cleanup failure is non-critical in tests
			}
			context.destroy();
			context = null;
		}
	}

	@Test
	void testChannelManagerCachesPerTarget() {
		context = createContext(GrpcChannelManager.class);

		GrpcChannelManager mgr = context.getBean(GrpcChannelManager.class);
		var ch1 = mgr.getChannel("localhost:9999");
		var ch2 = mgr.getChannel("localhost:9999");
		var ch3 = mgr.getChannel("localhost:8888");

		assertSame(ch1, ch2, "Same target should return same channel");
		assertNotSame(ch1, ch3, "Different target should return different channel");
		assertFalse(ch1.isShutdown());
	}

	@Test
	void testServerRunnerDoesNotStartWithoutServices() {
		context = createContext(GrpcServerRunner.class, GrpcChannelManager.class);

		GrpcServerRunner runner = context.getBean(GrpcServerRunner.class);
		assertNotNull(runner);
		assertEquals(-1, runner.getPort(), "Port should be -1 when no gRPC services are registered");
	}
}
