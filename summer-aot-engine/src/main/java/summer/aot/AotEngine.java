package summer.aot;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.IndexView;
import summer.core.BeanContainer;
import summer.core.bean.BeanDefinition;
import summer.core.bean.Scope;
import summer.core.bean.SharedDependencyResolver;

/**
 * Runtime AOT compiler for testing.
 *
 * <p>
 * Generates, compiles, and loads AOT code at test time — no Maven plugin
 * required. Results are cached by bean-closure hash so identical seeds skip
 * recompilation.
 * </p>
 *
 * <p>
 * This class lives in {@code summer-aot-engine} and is loaded reflectively by
 * {@code summer.test.Testing} to avoid a compile-time dependency cycle
 * (summer-data-jdbc → summer-test ← summer-aot-engine).
 * </p>
 */
public final class AotEngine {

	private static final java.util.concurrent.ConcurrentHashMap<String, BeanContainer> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	private AotEngine() {
	}

	/**
	 * Full AOT pipeline: discover → resolve → compile → load.
	 *
	 * @param index
	 *            Jandex index
	 * @param scope
	 *            bean scope (classpath or seed-isolated)
	 * @param visibleTypes
	 *            extra type names for cross-module {@code @ConditionalOnBean}
	 *            visibility, or {@code null} for standard evaluation
	 * @param cacheKey
	 *            unique cache key (deterministic — e.g. sorted seed names)
	 * @param externalBeans
	 *            pre-instantiated beans to register
	 * @return AOT-compiled BeanContainer
	 */
	public static BeanContainer buildAndCompile(IndexView index, Scope scope, Set<String> visibleTypes,
			String cacheKey, Object... externalBeans) {
		BeanContainer cached = CACHE.get(cacheKey);
		if (cached != null) {
			return cached;
		}

		List<BeanDefinition> beans = new BeanDiscovery(index).discover(scope, visibleTypes);
		List<BeanDefinition> sorted = new SharedDependencyResolver().resolve(beans);

		return compile(index, sorted, cacheKey, externalBeans);
	}

	/**
	 * Full AOT pipeline without cross-module visibleTypes (backward compat).
	 */
	public static BeanContainer buildAndCompile(IndexView index, Scope scope, String cacheKey,
			Object... externalBeans) {
		return buildAndCompile(index, scope, null, cacheKey, externalBeans);
	}

	/**
	 * Generate, compile, load, and return a BeanContainer from pre-sorted bean
	 * definitions.
	 *
	 * @param index
	 *            Jandex index
	 * @param sorted
	 *            topologically-sorted bean definitions (conditions already
	 *            evaluated)
	 * @param cacheKey
	 *            unique cache key (e.g. SHA-256 of seed names)
	 * @param externalBeans
	 *            pre-instantiated beans to register
	 * @return AOT-compiled BeanContainer
	 */
	public static BeanContainer compile(IndexView index, List<BeanDefinition> sorted, String cacheKey,
			Object... externalBeans) {
		// Check cache
		BeanContainer cached = CACHE.get(cacheKey);
		if (cached != null) {
			return cached;
		}

		try {
			// 1. Generate code to temp directory
			File tempDir = Files.createTempDirectory("summer-aot-").toFile();
			tempDir.deleteOnExit();

			WireMethodGenerator wireGen = new WireMethodGenerator();
			new AotContextGenerator(index, tempDir, wireGen).generate(sorted);
			new AotProxyGenerator().generate(sorted, index, tempDir);
			new RouteAdapterGenerator().generate(sorted, tempDir);

			// 2. Compile generated sources
			compileGeneratedSources(tempDir);

			// 3. Load and build
			Class<?> aotClass = Class.forName("summer.core.aot.GeneratedAotContext");
			BeanContainer container = (BeanContainer) aotClass.getMethod("build", Object[].class).invoke(null,
					(Object) externalBeans);

			// 4. Cache and return
			CACHE.put(cacheKey, container);
			return container;

		} catch (Exception e) {
			throw new RuntimeException("[Summer] AOT compilation failed", e);
		}
	}

	/**
	 * Clears the compilation cache. Useful between test suites.
	 */
	public static void clearCache() {
		CACHE.clear();
	}

	// ── Compilation ─────────────────────────────────────────────────────

	private static void compileGeneratedSources(File dir) throws Exception {
		List<File> sourceFiles = new ArrayList<>();
		collectJavaFiles(dir, sourceFiles);
		if (sourceFiles.isEmpty()) {
			return;
		}

		var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException(
					"No system Java compiler available. " + "Ensure the project runs on a JDK, not a JRE.");
		}

		String classpath = resolveClasspath();
		File out = new File(dir, "classes");
		out.mkdirs();

		var fm = compiler.getStandardFileManager(null, null, null);
		var units = fm.getJavaFileObjectsFromStrings(sourceFiles.stream().map(File::getAbsolutePath).toList());
		var diags = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();

		var task = compiler.getTask(null, fm, diags,
				List.of("-cp", classpath, "-d", out.getAbsolutePath(), "--release", "26"), null, units);

		if (!task.call()) {
			StringBuilder sb = new StringBuilder("[Summer] AOT compilation failed:\n");
			for (var diag : diags.getDiagnostics()) {
				sb.append("  ").append(diag).append("\n");
			}
			throw new RuntimeException(sb.toString());
		}
		fm.close();
	}

	private static void collectJavaFiles(File dir, List<File> result) {
		File[] files = dir.listFiles();
		if (files == null)
			return;
		for (File f : files) {
			if (f.isDirectory()) {
				collectJavaFiles(f, result);
			} else if (f.getName().endsWith(".java")) {
				result.add(f);
			}
		}
	}

	/**
	 * Resolves the classpath for compilation. Uses {@code java.class.path} system
	 * property (set by Maven Surefire, Gradle, and most IDEs). Falls back to
	 * constructing from the URLClassLoader.
	 */
	private static String resolveClasspath() {
		String cp = System.getProperty("java.class.path");
		if (cp != null && !cp.isEmpty()) {
			return cp;
		}
		// Fallback: build from classloader URLs
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		StringBuilder sb = new StringBuilder();
		while (cl instanceof java.net.URLClassLoader ucl) {
			for (java.net.URL url : ucl.getURLs()) {
				if (sb.length() > 0)
					sb.append(File.pathSeparatorChar);
				sb.append(url.getPath());
			}
			cl = ucl.getParent();
		}
		return sb.toString();
	}
}
