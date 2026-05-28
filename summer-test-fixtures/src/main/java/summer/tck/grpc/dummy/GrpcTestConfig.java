package summer.tck.grpc.dummy;

import summer.core.annotation.Configuration;
import summer.grpc.client.GrpcChannelManager;
import summer.grpc.test.echo.EchoServiceGrpc;

@Configuration
public class GrpcTestConfig {

	private final GrpcChannelManager channelManager;

	public GrpcTestConfig(GrpcChannelManager channelManager) {
		this.channelManager = channelManager;
	}

	@summer.core.annotation.Bean
	public EchoServiceGrpc.EchoServiceBlockingStub echoStub() {
		return channelManager.getBlockingStub(EchoServiceGrpc.class, "localhost:9090");
	}
}
