package com.github.dropguard.summer.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.dropguard.summer.aot.AotContextGenerator;
import com.github.dropguard.summer.aot.AotProxyGenerator;
import com.github.dropguard.summer.aot.JavaSourceFiles;
import com.github.dropguard.summer.aot.RouteAdapterGenerator;
import com.github.dropguard.summer.aot.WireMethodGenerator;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.SharedDependencyResolver;
import com.github.dropguard.summer.core.spi.RouteRegistrarLoader;
import com.github.dropguard.summer.engine.BeanDeployment;
import com.github.dropguard.summer.engine.Discovery;
import com.github.dropguard.summer.engine.SharedConditionEvaluator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        requiresDependencyResolution = ResolutionScope.TEST,
        requiresProject = true)
public class SummerMojo extends AbstractMojo {

    private static final Logger log = LoggerFactory.getLogger(SummerMojo.class);

    /** YAML binding for {@link #flipEngineToAot()}; jackson-dataformat-yaml is on the classpath. */
    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

    /** Tracks which generation step was in progress, for failure diagnostics. */
    private String currentBean;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
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
                log.info("[Summer] No Jandex index found, skipping");
                return;
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
            List<BeanDefinition> beans = Discovery.discover(deployment);
            log.info("[Summer] Discovered {} beans", beans.size());

            // SPI route collection (shared with the Runtime engine): loads every
            // RouteRegistrar on the classpath (e.g. summer-runtime-web's WebRouteScanner)
            // and merges routes / exception handlers into the candidate definitions
            // before condition evaluation, so AOT codegen sees the same web surface.
            RouteRegistrarLoader.mergeInto(RouteRegistrarLoader.load(beans), beans);

            new SharedConditionEvaluator().evaluate(beans);
            log.info("[Summer] After condition evaluation: {} beans", beans.size());

            SharedDependencyResolver resolver = new SharedDependencyResolver();
            List<BeanDefinition> sorted = resolver.resolve(beans);
            log.info("[Summer] Resolved {} beans", sorted.size());
            if (log.isDebugEnabled()) {
                for (BeanDefinition b : sorted) {
                    log.debug(
                            "[Summer]   bean: {} [factory {}#{}] archive={} params={}{}",
                            b.qualifiedName,
                            b.configClassName,
                            b.producerMethodName,
                            b.archiveName,
                            b.parameters.size(),
                            b.syntheticInstance != null ? " [synthetic]" : "");
                }
            }
            java.util.Set<String> usedNames = new java.util.HashSet<>();
            for (com.github.dropguard.summer.core.bean.BeanDefinition bean : sorted) {
                String baseName = bean.variableName;
                int suffix = 2;
                while (!usedNames.add(bean.variableName)) {
                    bean.variableName = baseName + suffix++;
                }
            }

            WireMethodGenerator wireGen = new WireMethodGenerator(index);
            currentBean = "(context)";
            new AotContextGenerator(index, generatedDir, wireGen).generate(sorted);
            currentBean = "(proxies)";
            new AotProxyGenerator().generate(sorted, index, generatedDir);
            currentBean = "(route-adapters)";
            // Route adapter imports web types — generate only when routes exist,
            // or non-web applications fail to compile the generated sources.
            if (sorted.stream().anyMatch(b -> !b.routes.isEmpty())) {
                new RouteAdapterGenerator().generate(sorted, generatedDir);
            }

            compileGeneratedSources(generatedDir);

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

    private void compileGeneratedSources(File generatedDir) throws Exception {
        List<File> sourceFiles = new ArrayList<>();
        collectJavaFiles(generatedDir, sourceFiles);
        if (sourceFiles.isEmpty()) return;

        log.debug("[Summer] Compiling " + sourceFiles.size() + " generated source(s)");

        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new MojoExecutionException("[Summer] No Java compiler available.");

        List<String> cp = new ArrayList<>();
        cp.add(outputDirectory.getAbsolutePath());
        for (Object obj : project.getArtifacts()) {
            Artifact a = (Artifact) obj;
            if (a.getFile() != null) cp.add(a.getFile().getAbsolutePath());
        }

        log.debug("[Summer] Compilation classpath: " + cp);

        File out = new File(outputDirectory.getAbsolutePath());
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
                                String.join(System.getProperty("path.separator"), cp),
                                "-d",
                                out.getAbsolutePath()),
                        null,
                        units);

        if (!task.call()) {
            for (var diag : diags.getDiagnostics()) {
                log.error("[Summer] " + diag);
            }
            throw new MojoExecutionException("[Summer] Compilation of generated sources failed");
        }
        fm.close();
    }

    /** Ensures the packaged {@code application.yml} selects the AOT engine. */
    private void flipEngineToAot() throws IOException {
        File classesDir = new File(outputDirectory.getAbsolutePath());
        File yml = new File(classesDir, "application.yml");
        classesDir.mkdirs();

        Map<String, Object> root;
        if (yml.exists()) {
            try (InputStream in = Files.newInputStream(yml.toPath())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> loaded = YAML_MAPPER.readValue(in, Map.class);
                root = loaded;
            }
        } else {
            root = new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> summer = (Map<String, Object>) root.get("summer");
        if (summer == null) {
            summer = new HashMap<>();
            root.put("summer", summer);
        }
        summer.put("engine", "aot");

        YAML_MAPPER.writeValue(yml, root);
        log.info("[Summer] Set summer.engine: aot for production build");
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
