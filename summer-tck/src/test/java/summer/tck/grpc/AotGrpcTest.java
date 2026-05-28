package summer.tck.grpc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import summer.core.ApplicationContext;
import summer.tck.aot.AotCompilation;
import summer.tck.compiler.MemoryCompiler;

public class AotGrpcTest extends AbstractGrpcTCK {

	private static MemoryCompiler compiler;

	@BeforeAll
	static void setup() {
		compiler = new MemoryCompiler();
		ClassLoader aotClassLoader = AotCompilation.compile(compiler, AotCompilation.FIXTURES_SRC + "/grpc/dummy");
		ApplicationContext ctx = AotCompilation.loadContext(aotClassLoader);
		startGrpcServer(ctx);
	}

	@AfterAll
	static void cleanup() {
		AotCompilation.cleanUp(compiler);
	}
}
