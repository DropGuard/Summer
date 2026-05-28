package summer.tck.aop;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import summer.core.ApplicationContext;
import summer.tck.aot.AotCompilation;
import summer.tck.compiler.MemoryCompiler;

public class AotAopTest extends AbstractAopTCK {

	private static MemoryCompiler compiler;
	private static ClassLoader aotClassLoader;

	@BeforeAll
	static void compileAot() {
		compiler = new MemoryCompiler();
		aotClassLoader = AotCompilation.compile(compiler, AotCompilation.FIXTURES_SRC + "/aop/dummy");
	}

	@AfterAll
	static void cleanup() {
		AotCompilation.cleanUp(compiler);
	}

	@Override
	protected ApplicationContext createContext() {
		return AotCompilation.loadContext(aotClassLoader);
	}
}
