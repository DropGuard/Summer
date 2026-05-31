package summer.tck.di;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import summer.core.ApplicationContext;
import summer.tck.aot.AotCompilation;
import summer.tck.compiler.MemoryCompiler;

/**
 * AOT test for generic interface dependency resolution.
 */
public class AotGenericInterfaceTest extends AbstractGenericInterfaceTCK {

	private static MemoryCompiler compiler;
	private static ClassLoader aotClassLoader;

	@BeforeAll
	static void compileAot() {
		compiler = new MemoryCompiler();
		aotClassLoader = AotCompilation.compile(compiler, AotCompilation.FIXTURES_SRC + "/di/generic");
	}

	@AfterAll
	static void cleanup() {
		AotCompilation.cleanUp(compiler);
	}

	@Override
	protected ApplicationContext createAndInitializeContext() {
		return AotCompilation.loadContext(aotClassLoader);
	}
}
