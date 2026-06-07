package summer.tck.grpc;

import summer.core.ApplicationContext;
import summer.grpc.GrpcInfrastructureConfiguration;
import summer.runtime.RuntimeApplicationContext;
import summer.runtime.RuntimeInfrastructureConfiguration;

/**
 * Runtime test for gRPC component discovery.
 */
public class RuntimeGrpcTCKTest extends AbstractGrpcTCK {

	@Override
	protected ApplicationContext createContext(Class<?>... components) {
		RuntimeApplicationContext ctx = new RuntimeApplicationContext();
		ctx.registerComponent(RuntimeInfrastructureConfiguration.class);
		ctx.registerComponent(GrpcInfrastructureConfiguration.class);
		for (Class<?> c : components) {
			ctx.registerComponent(c);
		}
		ctx.initializeBeans();
		return ctx;
	}
}
