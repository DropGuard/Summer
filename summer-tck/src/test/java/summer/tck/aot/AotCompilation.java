package summer.tck.aot;

import java.io.File;
import java.util.List;
import summer.core.ApplicationContext;
import summer.tck.compiler.MemoryCompiler;

public final class AotCompilation {

	/** Base path to test fixture sources, relative to the TCK module root. */
	public static final String FIXTURES_SRC = "../summer-test-fixtures/src/main/java/summer/tck";

	private AotCompilation() {
	}

	public static ClassLoader compile(MemoryCompiler compiler, String dummyPackagePath) {
		File sourceDir = new File(dummyPackagePath);
		File[] sourceFiles = sourceDir.listFiles((_, name) -> name.endsWith(".java"));
		if (sourceFiles == null || sourceFiles.length == 0) {
			throw new IllegalStateException("Could not find source files in " + sourceDir.getAbsolutePath());
		}
		return compiler.compileAndLoad(List.of(sourceFiles));
	}

	public static ApplicationContext loadContext(ClassLoader loader) {
		try {
			Class<?> aotCtxClass = loader.loadClass("summer.core.aot.GeneratedAotContext");
			ApplicationContext ctx = (ApplicationContext) aotCtxClass.getConstructor().newInstance();
			ApplicationContext.init(ctx);
			return ctx;
		} catch (Exception e) {
			throw new RuntimeException("Failed to load and instantiate GeneratedAotContext", e);
		}
	}

	public static void cleanUp(MemoryCompiler compiler) {
		if (compiler != null) {
			compiler.cleanUp();
		}
	}
}
