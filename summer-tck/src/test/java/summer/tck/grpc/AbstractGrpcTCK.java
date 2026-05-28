package summer.tck.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.grpc.client.GrpcChannelManager;
import summer.grpc.server.GrpcServerRunner;
import summer.grpc.test.echo.EchoRequest;
import summer.grpc.test.echo.EchoResponse;
import summer.grpc.test.echo.EchoServiceGrpc;

/**
 * Abstract gRPC Test Compatibility Kit.
 *
 * Defines the behavioral contract that BOTH the Runtime and AOT engines must
 * satisfy for gRPC server/client integration.
 *
 * Note: Concrete subclasses must call
 * {@link #startGrpcServer(ApplicationContext)} from their own @BeforeAll method
 * and assign to the static fields. This is because @BeforeAll must be static
 * and cannot be inherited normally.
 */
public abstract class AbstractGrpcTCK {

	protected static ApplicationContext context;
	protected static int actualPort;

	protected static void startGrpcServer(ApplicationContext ctx) {
		System.setProperty("summer.grpc.port", "0");
		context = ctx;
		GrpcServerRunner runner = context.getBean(GrpcServerRunner.class);
		try {
			runner.run(context);
		} catch (Exception e) {
			throw new RuntimeException("Failed to run GrpcServerRunner", e);
		}
		actualPort = runner.getPort();
	}

	@AfterAll
	static void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
		System.clearProperty("summer.grpc.port");
	}

	@Test
	void testGrpcServerAndClientStub() {
		GrpcChannelManager channelManager = context.getBean(GrpcChannelManager.class);
		EchoServiceGrpc.EchoServiceBlockingStub stub = channelManager.getBlockingStub(EchoServiceGrpc.class,
				"localhost:" + actualPort);
		assertNotNull(stub, "Stub should be created for the running gRPC server");

		EchoResponse response = stub.echo(EchoRequest.newBuilder().setMessage("Hello gRPC").build());
		assertEquals("ECHO: Hello gRPC", response.getMessage());
	}
}
