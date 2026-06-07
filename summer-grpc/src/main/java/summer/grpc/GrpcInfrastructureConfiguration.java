package summer.grpc;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.grpc.client.GrpcChannelManager;
import summer.grpc.server.GrpcServerRunner;

/**
 * Configuration for gRPC infrastructure beans.
 *
 * <p>
 * Provides {@link GrpcChannelManager} for client channel management and
 * {@link GrpcServerRunner} for server lifecycle.
 * </p>
 */
@Configuration
public class GrpcInfrastructureConfiguration {

	@Bean
	public GrpcChannelManager grpcChannelManager() {
		return new GrpcChannelManager();
	}

	@Bean
	public GrpcServerRunner grpcServerRunner() {
		return new GrpcServerRunner();
	}
}
