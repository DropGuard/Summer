package summer.tck.grpc;

import summer.core.BeanContainer;
import summer.runtime.RuntimeBeanContainerBuilder;

/**
 * Runtime test for gRPC component discovery.
 */
public class RuntimeGrpcTCKTest extends AbstractGrpcTCK {

	@Override
	protected BeanContainer createContext(Class<?>... configClasses) {
		return RuntimeBeanContainerBuilder.build();
	}
}
