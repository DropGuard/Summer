package com.github.dropguard.summer.plugin;

import com.github.dropguard.summer.aot.AotContextGenerator;
import com.github.dropguard.summer.aot.AotEngine;
import com.github.dropguard.summer.aot.AotProxyGenerator;
import com.github.dropguard.summer.aot.AotSourceCompiler;
import com.github.dropguard.summer.aot.JavaSourceFiles;
import com.github.dropguard.summer.aot.RouteAdapterGenerator;
import com.github.dropguard.summer.aot.WireMethodGenerator;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.engine.BeanDeployment;
import com.github.dropguard.summer.engine.BuildPipeline;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AOT code generation for Summer framework. Discovers beans from the Jandex index, resolves
 * dependencies, and generates AOT context, proxies, and route adapters.
 */
@Mojo(
        name = "generate-aot",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        // COMPILE_PLUS_RUNTIME, not TEST (the Quarkus BuildMojo scope): the plugin builds
        // the PRODUCTION AOT graph — test-scoped dependencies must not enter the index
        // (their jandex.idx would leak test-only beans into the generated container) nor
        // the generated-code compile classpath.
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresProject = true,
        threadSafe = true)
public class SummerMojo extends AbstractMojo {

    private static final Logger log = LoggerFactory.getLogger(SummerMojo.class);

    /** Tracks which generation step was in progress, for failure diagnostics. */
    private String currentBean;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File outputDirectory;

    /** Skips AOT generation (e.g. for fast local builds); set {@code -Dsummer.aot.skip=true}. */
    @Parameter(defaultValue = "false", property = "summer.aot.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            log.info("[Summer] AOT generation skipped (-Dsummer.aot.skip=true)");
            return;
        }

        // A POM-packaging module (aggregator / BOM) has no classes to AOT-compile.
        if ("pom".equals(project.getPackaging())) {
            log.info("[Summer] Skipping AOT generation for POM-packaging module");
            return;
        }

        File generatedDir = null;
        try {
            generatedDir = prepareGeneratedDir();
            CompositeIndex index = loadIndexes();
            if (index.getKnownClasses().isEmpty()) {
                // Silent skip here would package a jar whose config still says RUNTIME while the
                // build claims AOT generation ran. Fail loudly instead — this means the jandex
                // goal never produced an index (goal ordering) or the module has no classes.
                throw new MojoExecutionException(
                        "[Summer] No Jandex index found for AOT generation. Ensure"
                                + " jandex:index runs before summer:aot (see summer-build-parent"
                                + " bindings) and that the module compiles at least one class.");
            }

            log.info("[Summer] Starting AOT code generation");
            log.debug(
                    "[Summer] Loaded Jandex index with {} classes", index.getKnownClasses().size());

            BeanDeployment deployment = BeanDeployment.forProduction(index);
            log.info(
                    "[Summer] BeanDeployment: archives={} syntheticBeans={}",
                    deployment.archives(),
                    deployment.syntheticBeans().stream()
                            .map(b -> b.qualifiedName)
                            .collect(java.util.stream.Collectors.joining(",")));
            // The shared assembly core (discovery → conditions → routes → resolve → name dedup),
            // identical to the test-time AOT compiler's sequence — one implementation, one order.
            List<java.net.URL> urls = new java.util.ArrayList<>();
            urls.add(outputDirectory.toURI().toURL());
            for (Object obj : project.getArtifacts()) {
                org.apache.maven.artifact.Artifact a = (org.apache.maven.artifact.Artifact) obj;
                if (a.getFile() != null) {
                    urls.add(a.getFile().toURI().toURL());
                }
            }
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            List<BeanDefinition> sorted;
            // Bridges the PROJECT's artifacts into the thread context so ServiceLoader (route
            // registrar SPI, converters) can discover implementations that live on project
            // dependencies — they are invisible to the plugin's own classpath. Closed after use
            // to release jar handles (daemon-style Maven processes would otherwise pin them).
            try (java.net.URLClassLoader projectClassLoader =
                    new java.net.URLClassLoader(
                            urls.toArray(new java.net.URL[0]), originalClassLoader)) {
                Thread.currentThread().setContextClassLoader(projectClassLoader);
                try {
                    sorted = BuildPipeline.resolve(deployment, List.of()).sorted();
                } finally {
                    Thread.currentThread().setContextClassLoader(originalClassLoader);
                }
            }
            // Fail-fast for @Bean products with non-public return types (the generated code
            // references them cross-package); the test-time compiler enforces the same check.
            AotEngine.rejectNonPublicProducts(deployment, sorted);

            WireMethodGenerator wireGen = new WireMethodGenerator(index);
            currentBean = "(context)";
            new AotContextGenerator(index, generatedDir, wireGen).generate(sorted);
            currentBean = "(proxies)";
            new AotProxyGenerator().generate(sorted, index, generatedDir);
            currentBean = "(route-adapters)";
            // Route adapter imports web types — generate only when routes exist,
            // or non-web applications fail to compile the generated sources.
            if (sorted.stream().anyMatch(b -> !b.routes.isEmpty())) {
                new RouteAdapterGenerator().generate(sorted, index, generatedDir);
            }

            compileGeneratedSources(generatedDir);
            // Reconcile AFTER a successful compile: javac never deletes from target/classes, so
            // a bean renamed or removed between builds leaves its compiled .class behind and
            // gets packaged into the jar. parseSources + reconcile is the single mechanism.
            // Running this AFTER compile (not before) means a failed AOT generation leaves
            // target/classes in its previous successful state — IDE incremental reads and
            // subsequent clean rebuilds see a consistent view rather than a half-deleted one.
            reconcileAgainstSources(generatedDir);

            flipEngineToAot();

            currentBean = null;
            log.info("[Summer] AOT generation complete");

        } catch (Exception e) {
            String loc =
                    generatedDir != null
                            ? generatedDir.getAbsolutePath()
                            : "<before generation dir>";
            String at = currentBean != null ? " at step " + currentBean : "";
            throw new MojoExecutionException(
                    "[Summer] AOT generation failed"
                            + at
                            + ": "
                            + e.getMessage()
                            + " (generated sources in "
                            + loc
                            + ")",
                    e);
        }
    }

    private File prepareGeneratedDir() throws IOException {
        File dir = new File(project.getBasedir(), "target/generated-sources/aot");
        if (dir.exists()) {
            Files.walk(dir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        dir.mkdirs();
        return dir;
    }

    // ── Stale generated-class cleanup ───────────────────────────────────
    //
    // Generation wipes and rewrites the generated SOURCES every run, but javac only ever adds
    // to target/classes: when a bean is deleted or renamed, the previous run's compiled
    // $$Context/$$AotProxy/$$ConfigImpl classes survive there and get packaged into the jar.
    // We reconcile target/classes against the live source set AFTER the new generation has
    // compiled successfully — running it before generation leaves target/classes in a half-
    // deleted state if the AOT build then fails (IDE incremental reads, parallel module
    // builds, and the next clean rebuild all see an inconsistent view). No persistent state:
    // the live parse is recomputed every run and needs no cross-run bookkeeping.

    /**
     * Parses {@code src/main/java} + the just-prepared {@code generatedDir}, then deletes every
     * .class in {@code target/classes} that has no corresponding source. Single mechanism that
     * covers both user source changes and AOT-generated source changes.
     */
    private void reconcileAgainstSources(File generatedDir) throws IOException {
        List<File> roots = new ArrayList<>();
        for (String root : project.getCompileSourceRoots()) {
            File f = new File(root);
            if (f.isDirectory()) roots.add(f);
        }
        if (generatedDir != null && generatedDir.isDirectory()) {
            roots.add(generatedDir);
        }
        // No source roots means we cannot reason about which .class files are stale — bail
        // out rather than delete everything. This only happens in test fixtures that build
        // an artificial project; real Maven invocations always have at least one source root.
        if (roots.isEmpty()) {
            return;
        }
        Set<String> sourceClassNames = SummerSourceIndex.parseSources(roots);
        int removed = SummerSourceIndex.reconcile(outputDirectory, sourceClassNames);
        if (removed > 0) {
            log.info("[Summer] Reconciled target/classes: removed {} stale class(es)", removed);
        }
    }

    private void compileGeneratedSources(File generatedDir) throws Exception {
        List<File> sourceFiles = new ArrayList<>();
        collectJavaFiles(generatedDir, sourceFiles);
        if (sourceFiles.isEmpty()) return;

        log.debug("[Summer] Compiling " + sourceFiles.size() + " generated source(s)");

        List<String> cp = new ArrayList<>();
        cp.add(outputDirectory.getAbsolutePath());
        for (Object obj : project.getArtifacts()) {
            Artifact a = (Artifact) obj;
            if (a.getFile() != null) cp.add(a.getFile().getAbsolutePath());
        }

        log.debug("[Summer] Compilation classpath: " + cp);

        File out = new File(outputDirectory.getAbsolutePath());
        out.mkdirs();

        List<javax.tools.Diagnostic<? extends javax.tools.JavaFileObject>> diags =
                AotSourceCompiler.compile(
                        sourceFiles,
                        List.of(
                                "-cp",
                                String.join(System.getProperty("path.separator"), cp),
                                "-d",
                                out.getAbsolutePath()));

        if (!diags.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (var diag : diags) {
                log.error("[Summer] " + diag);
                sb.append(System.lineSeparator()).append(diag);
            }
            // The javac diagnostics travel IN the exception: a failed AOT build must be
            // debuggable from the failure message alone, not from scattered build-log lines.
            throw new MojoExecutionException(
                    "[Summer] Compilation of generated sources failed:" + sb);
        }
    }

    private static final String FLIP_MARKER =
            "# summer.engine set by summer-maven-plugin (AOT production build)";

    /**
     * Ensures the packaged {@code application.yml} selects the AOT engine — via a byte-preserving
     * TEXT edit. The previous implementation round-tripped the file through Jackson, which silently
     * destroyed comments and reordered keys in the shipped artifact. Only the {@code engine} line
     * is ever touched; everything else stays byte-for-byte.
     */
    private void flipEngineToAot() throws IOException {
        File classesDir = new File(outputDirectory.getAbsolutePath());
        File yml = new File(classesDir, "application.yml");
        classesDir.mkdirs();

        if (!yml.exists()) {
            String content = FLIP_MARKER + "\nsummer:\n  engine: aot\n";
            Files.writeString(yml.toPath(), content);
            log.info("[Summer] Created application.yml with summer.engine: aot");
            return;
        }

        List<String> lines = Files.readAllLines(yml.toPath());

        // Locate the top-level `summer:` block (column-0 key).
        int summerIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).matches("summer:\\s*(#.*)?")) {
                summerIdx = i;
                break;
            }
        }

        if (summerIdx < 0) {
            lines.add("");
            lines.add(FLIP_MARKER);
            lines.add("summer:");
            lines.add("  engine: aot");
            Files.write(yml.toPath(), lines);
            log.info("[Summer] Appended summer.engine: aot to application.yml");
            return;
        }

        // Scan the summer block: find an existing `engine:` line, and where its entries end
        // (block ends at EOF or at the next column-0 key).
        int blockEnd = lines.size();
        int engineIdx = -1;
        for (int i = summerIdx + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (!l.isBlank() && !l.startsWith(" ") && !l.startsWith("\t")) {
                blockEnd = i;
                break;
            }
            if (l.matches("\\s+engine:\\s*.*")) {
                engineIdx = i;
            }
        }
        String indent =
                engineIdx >= 0
                        ? lines.get(engineIdx).substring(0, lines.get(engineIdx).indexOf("engine:"))
                        : "  ";

        if (engineIdx >= 0) {
            // Replace only the value token after `engine:`; keep indentation and any comment.
            String l = lines.get(engineIdx);
            String rest = l.substring(l.indexOf("engine:") + "engine:".length());
            int comment = rest.indexOf('#');
            String tail = comment >= 0 ? rest.substring(comment) : "";
            lines.set(engineIdx, indent + "engine: aot" + (tail.isBlank() ? "" : " " + tail));
        } else {
            lines.add(blockEnd, indent + "engine: aot");
        }
        Files.write(yml.toPath(), lines);
        log.info("[Summer] Set summer.engine: aot in application.yml");
    }

    private void collectJavaFiles(File dir, List<File> result) {
        JavaSourceFiles.collect(dir, result);
    }

    private CompositeIndex loadIndexes() throws IOException {
        List<IndexView> indexes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        loadFromDirectory(outputDirectory, indexes, seen);
        for (Object obj : project.getArtifacts()) {
            Artifact a = (Artifact) obj;
            File f = a.getFile();
            if (f == null || !f.exists()) continue;
            if (f.isDirectory()) loadFromDirectory(f, indexes, seen);
            else if (f.getName().endsWith(".jar")) loadFromJar(f, indexes, seen);
        }
        return indexes.isEmpty()
                ? CompositeIndex.create(new ArrayList<>())
                : CompositeIndex.create(indexes);
    }

    private void loadFromDirectory(File dir, List<IndexView> indexes, Set<String> seen)
            throws IOException {
        Path p = dir.toPath().resolve("META-INF").resolve("jandex.idx");
        if (Files.exists(p) && seen.add(p.toString())) {
            try (InputStream is = Files.newInputStream(p)) {
                indexes.add(new IndexReader(is).read());
            }
        }
    }

    private void loadFromJar(File file, List<IndexView> indexes, Set<String> seen) {
        try (var jar = new java.util.jar.JarFile(file)) {
            var e = jar.getJarEntry("META-INF/jandex.idx");
            if (e == null || !seen.add(file.getAbsolutePath())) {
                return;
            }
            try (InputStream is = jar.getInputStream(e)) {
                indexes.add(new IndexReader(is).read());
            } catch (IOException ex) {
                // A declared-but-corrupt index would otherwise be dropped silently, hiding
                // that jar's beans from discovery (confusing NoSuchBeanException later).
                // Warn instead of failing: the jar may be an optional dependency.
                log.warn("[Summer] Ignoring corrupt Jandex index in {}: {}", file, ex.getMessage());
            }
        } catch (IOException ignored) {
            // Not a readable jar (directory / empty artifact) — skip.
        }
    }
}
