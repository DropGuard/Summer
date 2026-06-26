package summer.grpc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCalls;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.fixtures.GrpcTestConfig;
import summer.grpc.GrpcInfrastructureConfiguration;
import summer.runtime.RuntimeBeanContainerBuilder;

public class GrpcInterceptorIntegrationTest {

	@BeforeAll
	public static void setupProperty() {
		System.setProperty("summer.grpc.port", "0");
	}

	@Test
	public void testGrpcServerInterceptorDiscovery() throws Exception {
		var ctx = RuntimeBeanContainerBuilder.buildFromSeeds(GrpcTestConfig.class,
				GrpcInfrastructureConfiguration.class);

		GrpcServerRunner serverRunner = ctx.getBean(GrpcServerRunner.class);
		serverRunner.run(ctx);
		int port = serverRunner.getPort();
		assertTrue(port > 0, "Server should be bound to a valid port");

		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();

		try {
			String response = ClientCalls.blockingUnaryCall(channel, GrpcTestConfig.TEST_METHOD, CallOptions.DEFAULT,
					"RequestData");
			assertEquals("Hello Intercepted!", response);
		} finally {
			channel.shutdown();
			serverRunner.close();
			ctx.close();
		}
	}
}
