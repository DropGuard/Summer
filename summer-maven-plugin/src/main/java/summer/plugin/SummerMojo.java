package summer.plugin;

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
import summer.aot.AotContextGenerator;
import summer.aot.AotProxyGenerator;
import summer.aot.RouteAdapterGenerator;
import summer.aot.WireMethodGenerator;
import summer.core.Discovery;
import summer.core.bean.BeanDefinition;
import summer.core.bean.BeanDeployment;
import summer.core.bean.SharedDependencyResolver;

/**
 * AOT code generation for Summer framework. Discovers beans from the Jandex
 * index, resolves dependencies, and generates AOT context, proxies, and route
 * adapters.
 */
@Mojo(name = "generate-aot", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.TEST, requiresProject = true)
public class SummerMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
	private File outputDirectory;

	@Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
	private File testOutputDirectory;

	@Parameter(property = "summer.aot.testPhase", defaultValue = "false")
	private boolean testPhase;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		boolean isTestPhase = testPhase;

		// In the test phase, AOT code generation was previously driven by
		// @WithFixtures annotations, which have been removed. The generated
		// LocalContext classes served a TCK pattern that is now handled by
		// the whole-application test universe (Quarkus-aligned @SummerTest) --
		// AOT generation at process-test-classes is no longer needed by any test.
		if (isTestPhase) {
			getLog().info(
					"[Summer] Test-phase AOT disabled -- @WithFixtures has been removed. @SummerTest uses the full test universe.");
			return;
		}

		try {
			CompositeIndex index = loadIndexes(false);
			if (index.getKnownClasses().isEmpty()) {
				getLog().info("[Summer] No Jandex index found, skipping");
				return;
			}

			// AOT code generation
			getLog().info("[Summer] Starting AOT code generation");
			getLog().debug("[Summer] Loaded Jandex index with " + index.getKnownClasses().size() + " classes");

			File generatedDir = prepareGeneratedDir(false);

			// Unified discovery (shared by both engines) over the production index.
			// Discovery is engine-agnostic and consumes a BeanDeployment; the production
			// build wraps its merged CompositeIndex as a single-module universe.
			List<BeanDefinition> beans = Discovery.discover(BeanDeployment.forProduction(index,
					java.util.Collections.emptyMap(), java.util.Collections.emptyMap()));
			getLog().debug("[Summer] Discovered " + beans.size() + " beans");

			SharedDependencyResolver resolver = new SharedDependencyResolver();
			List<BeanDefinition> sorted = resolver.resolve(beans);
			getLog().debug("[Summer] Resolved " + sorted.size() + " beans");
			java.util.Set<String> usedNames = new java.util.HashSet<>();
			for (summer.core.bean.BeanDefinition bean : sorted) {
				String baseName = bean.variableName;
				int suffix = 2;
				while (!usedNames.add(bean.variableName)) {
					bean.variableName = baseName + suffix++;
				}
			}

			WireMethodGenerator wireGen = new WireMethodGenerator();
			new AotContextGenerator(index, generatedDir, wireGen).generate(sorted);
			new AotProxyGenerator().generate(sorted, index, generatedDir);
			new RouteAdapterGenerator().generate(sorted, generatedDir);

			compileGeneratedSources(generatedDir, false);

			getLog().info("[Summer] AOT generation complete");

		} catch (Exception e) {
			throw new MojoExecutionException("[Summer] AOT generation failed: " + e.getMessage(), e);
		}
	}

	private File prepareGeneratedDir(boolean isTestPhase) throws IOException {
		String name = isTestPhase ? "target/generated-test-sources/aot" : "target/generated-sources/aot";
		File dir = new File(project.getBasedir(), name);
		if (dir.exists()) {
			Files.walk(dir.toPath()).sorted(java.util.Comparator.reverseOrder()).map(Path::toFile)
					.forEach(File::delete);
		}
		dir.mkdirs();
		return dir;
	}

	// ---- compilation, index loading ----

	private String getCompileOutputDir(boolean isTestPhase) {
		return isTestPhase ? testOutputDirectory.getAbsolutePath() : outputDirectory.getAbsolutePath();
	}

	private void compileGeneratedSources(File generatedDir, boolean isTestPhase) throws Exception {
		List<File> sourceFiles = new ArrayList<>();
		collectJavaFiles(generatedDir, sourceFiles);
		if (sourceFiles.isEmpty())
			return;

		getLog().debug("[Summer] Compiling " + sourceFiles.size() + " generated source(s)");

		var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		if (compiler == null)
			throw new MojoExecutionException("[Summer] No Java compiler available.");

		List<String> cp = new ArrayList<>();
		cp.add(outputDirectory.getAbsolutePath());
		if (isTestPhase)
			cp.add(testOutputDirectory.getAbsolutePath());
		for (Object obj : project.getArtifacts()) {
			Artifact a = (Artifact) obj;
			if (a.getFile() != null)
				cp.add(a.getFile().getAbsolutePath());
		}

		getLog().debug("[Summer] Compilation classpath: " + cp);

		File out = new File(getCompileOutputDir(isTestPhase));
		out.mkdirs();

		var fm = compiler.getStandardFileManager(null, null, null);
		var units = fm.getJavaFileObjectsFromStrings(sourceFiles.stream().map(File::getAbsolutePath).toList());
		var diags = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
		var task = compiler.getTask(null, fm, diags,
				List.of("-cp", String.join(System.getProperty("path.separator"), cp), "-d", out.getAbsolutePath()),
				null, units);

		if (!task.call()) {
			for (var diag : diags.getDiagnostics()) {
				getLog().error("[Summer] " + diag);
			}
			throw new MojoExecutionException("[Summer] Compilation of generated sources failed");
		}
		fm.close();
	}

	private void collectJavaFiles(File dir, List<File> result) {
		File[] files = dir.listFiles();
		if (files == null)
			return;
		for (File f : files) {
			if (f.isDirectory())
				collectJavaFiles(f, result);
			else if (f.getName().endsWith(".java"))
				result.add(f);
		}
	}

	private CompositeIndex loadIndexes(boolean isTestPhase) throws IOException {
		List<IndexView> indexes = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		loadFromDirectory(outputDirectory, indexes, seen);
		if (isTestPhase && testOutputDirectory.exists())
			loadFromDirectory(testOutputDirectory, indexes, seen);
		for (Object obj : project.getArtifacts()) {
			Artifact a = (Artifact) obj;
			File f = a.getFile();
			if (f == null || !f.exists())
				continue;
			if (f.isDirectory())
				loadFromDirectory(f, indexes, seen);
			else if (f.getName().endsWith(".jar"))
				loadFromJar(f, indexes, seen);
		}
		return indexes.isEmpty() ? CompositeIndex.create(new ArrayList<>()) : CompositeIndex.create(indexes);
	}

	private void loadFromDirectory(File dir, List<IndexView> indexes, Set<String> seen) throws IOException {
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
			if (e != null && seen.add(file.getAbsolutePath())) {
				try (InputStream is = jar.getInputStream(e)) {
					indexes.add(new IndexReader(is).read());
				}
			}
		} catch (Exception ignored) {
		}
	}
}