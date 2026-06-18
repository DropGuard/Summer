package summer.tck.grpc;

import summer.core.ApplicationContext;
import summer.grpc.GrpcInfrastructureConfiguration;
import summer.runtime.RuntimeApplicationContext;

/**
 * Runtime test for gRPC component discovery.
 */
public class RuntimeGrpcTCKTest extends AbstractGrpcTCK {

	@Override
	protected ApplicationContext createContext(Class<?>... components) {
		var builder = RuntimeApplicationContext.builder().registerComponent(GrpcInfrastructureConfiguration.class);
		for (Class<?> c : components) {
			builder.registerComponent(c);
		}
		return builder.build();
	}
}
