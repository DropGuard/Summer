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
        rejectNonPublicProducts(moduleIndex, sorted);

        return compile(moduleIndex, sorted, cacheKey, className, mocks, overrides);
    }

    /**
     * The generated wiring references every active {@code @Bean} product by class name from another
     * package — a package-private (or otherwise non-public) return type is not accessible
     * cross-package and breaks the generated code. Checked after condition evaluation + resolution,
     * so products that are conditioned out on this path (e.g. a Runtime-only registrar) are
     * legitimately skipped and never rejected.
     */
    private static void rejectNonPublicProducts(
            BeanDeployment moduleIndex, List<BeanDefinition> sorted) {
        IndexView index = moduleIndex.discoveryIndex();
        for (BeanDefinition bean : sorted) {
            if (!bean.isFactoryMethod()) continue;
            org.jboss.jandex.ClassInfo product =
                    index.getClassByName(org.jboss.jandex.DotName.createSimple(bean.qualifiedName));
            // JVMS 4.6 access flags — java.lang.reflect.Modifier is banned outside the runtime
            // layer (the AOT path is reflection-free by design, see ReflectionConfinementTest).
            final short ACC_PUBLIC = 0x0001;
            if (product != null && (product.flags() & ACC_PUBLIC) == 0) {
                throw new com.github.dropguard.summer.core.exception.BeanCreationException(
                        "@Bean return type must be public: "
                                + bean.qualifiedName
                                + " — the AOT engine generates a cross-package reference to the"
                                + " product class and a non-public type is not accessible there.");
            }
        }
    }

    /**
     * Generates, compiles, and loads a BeanContainer from pre-sorted bean definitions. Internal
     * compilation stage of {@link #buildAndCompile} — there is no in-memory container cache; every
     * {@code buildAndCompile} call runs discovery + generation + compilation in full.
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

        String classpath = resolveClasspath();
        File out = new File(dir, "classes");
        out.mkdirs();

        List<javax.tools.Diagnostic<? extends javax.tools.JavaFileObject>> diags =
                AotSourceCompiler.compile(
                        sourceFiles,
                        List.of(
                                "-cp",
                                classpath,
                                "-d",
                                out.getAbsolutePath(),
                                "--release",
                                String.valueOf(Runtime.version().feature())));

        if (!diags.isEmpty()) {
            StringBuilder sb = new StringBuilder("[Summer] AOT compilation failed:\n");
            for (var diag : diags) {
                sb.append("  ").append(diag).append("\n");
            }
            throw new RuntimeException(sb.toString());
        }
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
