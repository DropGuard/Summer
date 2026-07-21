package summer.aot;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.IndexView;
import summer.core.BeanContainer;
import summer.core.DiEngine;
import summer.core.Discovery;
import summer.core.bean.BeanDefinition;
import summer.core.bean.BeanDeployment;
import summer.core.bean.MockedBean;
import summer.core.bean.SharedConditionEvaluator;
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
	 * @param cacheKey
	 *            unique cache key (deterministic). Must encode profile override
	 *            content + mocked types, or two tests that differ only by
	 *            {@code @Mock} will silently share one container.
	 * @param mocks
	 *            mocked beans ( type + instance) from {@code @Mock} parameters
	 * @return AOT-compiled BeanContainer
	 */
	public static BeanContainer buildAndCompile(IndexView index, String cacheKey, MockedBean[] mocks) {
		return buildAndCompile(index, cacheKey, AotContextGenerator.CLASS_NAME, mocks, java.util.Map.of());
	}

	/**
	 * Full AOT pipeline: discover → resolve → generate → compile → load.
	 *
	 * <p>
	 * Cache check happens once, here at the outermost boundary: if a container for
	 * this {@code cacheKey} already exists it is returned immediately, skipping all
	 * discovery/compilation work. The {@code cacheKey} must encode everything that
	 * affects the generated graph — profile override content and mocked types — or
	 * two distinct tests would silently share one container.
	 * </p>
	 *
	 * @param index
	 *            Jandex index
	 * @param cacheKey
	 *            unique cache key (deterministic, must encode profile override
	 *            content + mocked types)
	 * @param className
	 *            generated class name (without package). The JVM loads a class name
	 *            at most once per run, so distinct test containers must not share a
	 *            name — tests pass a profile-derived name while the production path
	 *            keeps the default {@link AotContextGenerator#CLASS_NAME}.
	 * @param mocks
	 *            mocked beans (target type + instance) from {@code @Mock}
	 *            parameters
	 * @param overrides
	 *            resolved {@code @TestProfile} content (empty map when none)
	 * @return AOT-compiled BeanContainer
	 */
	public static BeanContainer buildAndCompile(IndexView index, String cacheKey, String className, MockedBean[] mocks,
			java.util.Map<String, Object> overrides) {
		// No per-module split available — treat the whole index as one module.
		return buildAndCompile(BeanDeployment.forNarrow(index), cacheKey, className, mocks, overrides);
	}

	/**
	 * Full AOT pipeline with an explicit {@link BeanDeployment}. Preferred for
	 * seeded (narrow) universes: discovery iterates only the modules the index
	 * retains, so a {@code @SummerTest(classes=...)} seed closure is honoured
	 * instead of the whole classpath — the previous {@code IndexView}-only path
	 * silently re-discovered the entire universe and ignored the seeds.
	 */
	public static BeanContainer buildAndCompile(BeanDeployment moduleIndex, String cacheKey, String className,
			MockedBean[] mocks, java.util.Map<String, Object> overrides) {
		BeanContainer cached = CACHE.get(cacheKey);
		if (cached != null) {
			return cached;
		}

		List<BeanDefinition> beans = Discovery.discover(moduleIndex);
		// Discovery-stage mock replacement: remove the real definitions of every
		// mocked type so they are never generated/instantiated. Shared with Runtime
		// via SharedConditionEvaluator, so concrete-class @Mock behaves identically on
		// both engines (previously the AOT wire method overwrote the mock by class
		// key and lost).
		Set<String> mockedTypeNames = new java.util.HashSet<>();
		for (MockedBean mocked : mocks) {
			mockedTypeNames.add(mocked.targetTypeName());
		}
		new SharedConditionEvaluator().evaluate(beans, mockedTypeNames, moduleIndex);
		List<BeanDefinition> sorted = new SharedDependencyResolver().resolve(beans, java.util.Arrays.asList(mocks));

		return compile(moduleIndex, sorted, cacheKey, className, mocks, overrides);
	}

	/**
	 * Generates, compiles, loads, and caches a BeanContainer from pre-sorted bean
	 * definitions. Internal compilation stage of {@link #buildAndCompile} — the
	 * cache check lives in {@code buildAndCompile}, not here, so the early-return
	 * optimization (skip discovery + compilation on a hit) is not duplicated.
	 *
	 * @param moduleIndex
	 *            the module index (its merged view is used for annotation
	 *            resolution during code generation)
	 * @param sorted
	 *            topologically-sorted bean definitions (conditions already
	 *            evaluated, mocks already removed)
	 * @param cacheKey
	 *            unique cache key (e.g. SHA-256 of seed names); must match the key
	 *            checked in {@code buildAndCompile}
	 * @param className
	 *            generated class name (without package)
	 * @param mocks
	 *            mocked beans (target type + instance)
	 * @param overrides
	 *            resolved {@code @TestProfile} content (empty map when none)
	 * @return AOT-compiled BeanContainer
	 */
	private static BeanContainer compile(BeanDeployment moduleIndex, List<BeanDefinition> sorted, String cacheKey,
			String className, MockedBean[] mocks, java.util.Map<String, Object> overrides) {
		try {
			// 1. Generate code to temp directory
			File tempDir = Files.createTempDirectory("summer-aot-").toFile();
			tempDir.deleteOnExit();
			IndexView index = moduleIndex.discoveryIndex();

			WireMethodGenerator wireGen = new WireMethodGenerator(overrides);
			new AotContextGenerator(index, tempDir, wireGen, overrides).generate(sorted, className, mocks);
			new AotProxyGenerator().generate(sorted, index, tempDir);
			new RouteAdapterGenerator().generate(sorted, tempDir);

			// 2. Compile generated sources
			compileGeneratedSources(tempDir);

			// 3. Load and build — reflective loading of the compiled engine class is
			// delegated to the single framework-recognized loader in DiEngine, so no
			// reflection leaks into the AOT module. The generated build(MockedBean[])
			// registers each mock under its declared target type (real definitions
			// removed at discovery). The scratch output dir is passed as an extra
			// classpath element so the loader finds the just-compiled .class (it is
			// not, and must not be, on the application classpath).
			File classesDir = new File(tempDir, "classes");
			java.net.URL classesUrl = classesDir.toURI().toURL();
			BeanContainer container = DiEngine.loadCompiledEngine(AotContextGenerator.PACKAGE + "." + className,
					new java.net.URL[]{classesUrl}, (Object) mocks);

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
