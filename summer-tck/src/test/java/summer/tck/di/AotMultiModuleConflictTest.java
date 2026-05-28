package summer.tck.di;

import summer.tck.aot.AotCompilation;
import summer.tck.compiler.MemoryCompiler;

public class AotMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {

	@Override
	protected void triggerConflict() {
		MemoryCompiler compiler = new MemoryCompiler();
		try {
			AotCompilation.compile(compiler, AotCompilation.FIXTURES_SRC + "/di/conflict");
		} finally {
			compiler.cleanUp();
		}
	}
}
