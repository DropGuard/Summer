package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.core.bean.SharedDependencyResolver;
import com.github.dropguard.summer.core.exception.AotCompilationException;
import com.github.dropguard.summer.core.spi.RouteRegistrarLoader;
import com.github.dropguard.summer.engine.BeanDeployment;
import com.github.dropguard.summer.engine.DiEngine;
import com.github.dropguard.summer.engine.Discovery;
import com.github.dropguard.summer.engine.SharedConditionEvaluator;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime AOT compiler for testing.
 *
 * <p>Generates, compiles, and loads AOT code at test time — no Maven plugin required. The {@code
 * cacheKey} / {@code className} pair must be unique per test universe because the JVM loads a
 * generated class once: there is no in-memory container cache here, each distinct key triggers a
 * fresh compile-and-load (identical keys would collide on the class name, not dedupe).
 *
 * <p>This class lives in {@code summer-aot-engine} and is invoked by the {@code ContainerEngine}
 * SPI implementation {@link AotContainer} (resolved via {@code ContainerEngines.forEngine}) to
 * avoid a compile-time dependency cycle (summer-data-jdbc → summer-test ← summer-aot-engine).
 */
@Internal
public final class AotEngine {

    private static final Logger log = LoggerFactory.getLogger(AotEngine.class);

    private AotEngine() {}

    /**
     * Full AOT pipeline: discover → resolve → compile → load.
     *
     * @param index Jandex index
     * @param cacheKey unique cache key (deterministic). Must encode profile override content +
     *     mocked types, or two tests that differ only by {@code @Mock} will silently share one
     *     container.
     * @param mocks mocked beans ( type + instance) from {@code @Mock} parameters
     * @return AOT-compiled BeanContainer
     */
    public static BeanContainer buildAndCompile(
            IndexView index, String cacheKey, MockedBean[] mocks) {
        return buildAndCompile(
                index, cacheKey, AotContextGenerator.CLASS_NAME, mocks, java.util.Map.of());
    }

    /**
     * Full AOT pipeline: discover → resolve → generate → compile → load.
     *
     * <p>There is no container cache — every call compiles and loads fresh. The {@code cacheKey}
     * exists so tests can derive a deterministic, per-universe {@code className}: the JVM loads a
     * generated class at most once, so distinct test universes must not share a name (identical
     * keys would collide on the class name, not reuse a container).
     *
     * @param index Jandex index
     * @param cacheKey unique cache key (deterministic, must encode profile override content +
     *     mocked types)
     * @param className generated class name (without package). The JVM loads a class name at most
     *     once per run, so distinct test containers must not share a name — tests pass a
     *     profile-derived name while the production path keeps the default {@link
     *     AotContextGenerator#CLASS_NAME}.
     * @param mocks mocked beans (target type + instance) from {@code @Mock} parameters
     * @param overrides resolved {@code @TestProfile} content (empty map when none)
     * @return AOT-compiled BeanContainer
     */
    public static BeanContainer buildAndCompile(
            IndexView index,
            String cacheKey,
            String className,
            MockedBean[] mocks,
            java.util.Map<String, Object> overrides) {
        // No per-module split available — treat the whole index as one module.
        return buildAndCompile(
                BeanDeployment.forNarrow(index), cacheKey, className, mocks, overrides);
    }

    /**
     * Full AOT pipeline with an explicit {@link BeanDeployment}. Preferred for seeded (narrow)
     * universes: discovery iterates only the modules the index retains, so a
     * {@code @SummerTest(classes=...)} seed closure is honoured instead of the whole classpath —
     * the previous {@code IndexView}-only path silently re-discovered the entire universe and
     * ignored the seeds.
     */
    public static BeanContainer buildAndCompile(
            BeanDeployment moduleIndex,
            String cacheKey,
            String className,
            MockedBean[] mocks,
            java.util.Map<String, Object> overrides) {
        List<BeanDefinition> beans = Discovery.discover(moduleIndex);
        // SPI route collection (shared with the Runtime engine): loads every
        // RouteRegistrar on the classpath (e.g. summer-runtime-web's WebRouteScanner)
        // and merges routes / exception handlers into the candidate definitions
        // before condition evaluation, so AOT codegen sees the same web surface.
        RouteRegistrarLoader.mergeInto(RouteRegistrarLoader.load(beans), beans);
        // Discovery-stage mock replacement: remove the real definitions of every
        // mocked type so they are never generated/instantiated. Shared with Runtime
        // via SharedConditionEvaluator, so concrete-class @Mock behaves identically on
        // both engines (previously the AOT wire method overwrote the mock by class
        // key and lost).
        Set<String> mockedTypeNames = new java.util.HashSet<>();
        for (MockedBean mocked : mocks) {
            mockedTypeNames.add(mocked.targetTypeName());
        }
        new SharedConditionEvaluator().evaluate(beans, mockedTypeNames);
        List<BeanDefinition> sorted =
                new SharedDependencyResolver().resolve(beans, java.util.Arrays.asList(mocks));

        return compile(moduleIndex, sorted, cacheKey, className, mocks, overrides);
    }

    /**
     * Generates, compiles, loads, and caches a BeanContainer from pre-sorted bean definitions.
     * Internal compilation stage of {@link #buildAndCompile} — the cache check lives in {@code
     * buildAndCompile}, not here, so the early-return optimization (skip discovery + compilation on
     * a hit) is not duplicated.
     *
     * @param moduleIndex the module index (its merged view is used for annotation resolution during
     *     code generation)
     * @param sorted topologically-sorted bean definitions (conditions already evaluated, mocks
     *     already removed)
     * @param cacheKey unique cache key (e.g. SHA-256 of seed names); must match the key checked in
     *     {@code buildAndCompile}
     * @param className generated class name (without package)
     * @param mocks mocked beans (target type + instance)
     * @param overrides resolved {@code @TestProfile} content (empty map when none)
     * @return AOT-compiled BeanContainer
     */
    private static BeanContainer compile(
            BeanDeployment moduleIndex,
            List<BeanDefinition> sorted,
            String cacheKey,
            String className,
            MockedBean[] mocks,
            java.util.Map<String, Object> overrides) {
        try {
            log.debug(
                    "[Summer] AOT compile: cacheKey={} className={} beans={}",
                    cacheKey,
                    className,
                    sorted.size());
            // 1. Generate code to temp directory
            File tempDir = Files.createTempDirectory("summer-aot-").toFile();
            tempDir.deleteOnExit();
            IndexView index = moduleIndex.discoveryIndex();

            WireMethodGenerator wireGen = new WireMethodGenerator(index);
            log.debug("[Summer] AOT phase: generate context");
            new AotContextGenerator(index, tempDir, wireGen, overrides)
                    .generate(sorted, className, mocks);
            new AotProxyGenerator().generate(sorted, index, tempDir);
            // Route adapter imports web types — generate only when routes exist,
            // or non-web applications fail to compile the generated sources.
            if (sorted.stream().anyMatch(b -> !b.routes.isEmpty())) {
                new RouteAdapterGenerator().generate(sorted, tempDir);
            }

            // 2. Compile generated sources
            log.debug("[Summer] AOT phase: compile generated sources");
            compileGeneratedSources(tempDir);

            // 3. Load and build. The generated context registers engine-provided beans
            // (IndexView, RuntimeDiMarker, ...) from the candidate list — no reflection
            // needed. Reflective loading is delegated to DiEngine, the single framework
            // loader. The scratch output dir is passed as an extra classpath element so
            // the loader finds the just-compiled .class (not on the application cp).
            log.debug("[Summer] AOT phase: load and build");
            File classesDir = new File(tempDir, "classes");
            java.net.URL classesUrl = classesDir.toURI().toURL();
            BeanContainer container =
                    DiEngine.loadCompiledEngine(
                            AotContextGenerator.PACKAGE + "." + className,
                            new java.net.URL[] {classesUrl},
                            // Pack the MockedBean[] as a single Object element so the
                            // reflective lookup hits the sole build(Object...) entry point;
                            // the generated build unpacks MockedBean[] elements and registers
                            // mocks by declared type.
                            (Object) mocks);

            log.debug("[Summer] AOT compile complete: cacheKey={}", cacheKey);
            return container;

        } catch (Exception e) {
            throw new AotCompilationException(
                    "[Summer] AOT compilation failed: cacheKey="
                            + cacheKey
                            + " className="
                            + className
                            + " beans="
                            + sorted.size(),
                    e);
        }
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
                    "No system Java compiler available. "
                            + "Ensure the project runs on a JDK, not a JRE.");
        }

        String classpath = resolveClasspath();
        File out = new File(dir, "classes");
        out.mkdirs();

        var fm = compiler.getStandardFileManager(null, null, null);
        var units =
                fm.getJavaFileObjectsFromStrings(
                        sourceFiles.stream().map(File::getAbsolutePath).toList());
        var diags = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();

        var task =
                compiler.getTask(
                        null,
                        fm,
                        diags,
                        List.of(
                                "-cp",
                                classpath,
                                "-d",
                                out.getAbsolutePath(),
                                "--release",
                                String.valueOf(Runtime.version().feature())),
                        null,
                        units);

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
        JavaSourceFiles.collect(dir, result);
    }

    /**
     * Resolves the classpath for compilation. Uses {@code java.class.path} system property (set by
     * Maven Surefire, Gradle, and most IDEs). Falls back to constructing from the URLClassLoader.
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
                if (sb.length() > 0) sb.append(File.pathSeparatorChar);
                sb.append(url.getPath());
            }
            cl = ucl.getParent();
        }
        return sb.toString();
    }
}
