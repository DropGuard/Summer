package summer.tck.grpc;

import org.junit.jupiter.api.BeforeAll;
import summer.core.ApplicationContext;
import summer.scanner.runtime.RuntimeDiEngine;
import summer.tck.grpc.dummy.GrpcTestConfig;

public class RuntimeGrpcTest extends AbstractGrpcTCK {

	@BeforeAll
	static void setup() {
		ApplicationContext ctx = new RuntimeDiEngine().create(GrpcTestConfig.class);
		startGrpcServer(ctx);
	}
}
