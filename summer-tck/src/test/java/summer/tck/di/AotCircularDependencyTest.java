package summer.tck.di;

import summer.tck.aot.AotCompilation;
import summer.tck.compiler.MemoryCompiler;

public class AotCircularDependencyTest extends AbstractCircularDependencyTCK {

	@Override
	protected void triggerCircularDependency() {
		MemoryCompiler compiler = new MemoryCompiler();
		try {
			AotCompilation.compile(compiler, AotCompilation.FIXTURES_SRC + "/di/circular");
		} finally {
			compiler.cleanUp();
		}
	}
}
