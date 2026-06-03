package summer.tck.grpc;

import summer.core.ApplicationContext;
import summer.runtime.ReflectionMethodInvoker;
import summer.runtime.RuntimeApplicationContext;

/**
 * Runtime test for gRPC component discovery.
 */
public class RuntimeGrpcTCKTest extends AbstractGrpcTCK {

	@Override
	protected ApplicationContext createContext(Class<?>... components) {
		RuntimeApplicationContext ctx = new RuntimeApplicationContext();
		ctx.registerComponent(ReflectionMethodInvoker.class);
		for (Class<?> c : components) {
			ctx.registerComponent(c);
		}
		ctx.initializeBeans();
		return ctx;
	}
}
