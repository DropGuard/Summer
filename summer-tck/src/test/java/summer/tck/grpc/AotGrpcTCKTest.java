package summer.tck.grpc;

import summer.core.BeanContainer;

/**
 * AOT test for gRPC component discovery.
 */
public class AotGrpcTCKTest extends AbstractGrpcTCK {

	@Override
	protected BeanContainer createContext(Class<?>... configClasses) {
		try {
			
			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
