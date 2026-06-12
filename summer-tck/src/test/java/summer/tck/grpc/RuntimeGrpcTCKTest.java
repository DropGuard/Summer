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
		RuntimeApplicationContext ctx = new RuntimeApplicationContext();
		ctx.scan();
		ctx.registerComponent(GrpcInfrastructureConfiguration.class);
		for (Class<?> c : components) {
			ctx.registerComponent(c);
		}
		ctx.initializeBeans();
		return ctx;
	}
}
