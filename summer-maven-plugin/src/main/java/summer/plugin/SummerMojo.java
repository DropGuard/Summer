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
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;

/**
 * Maven plugin for Summer framework AOT code generation.
 *
 * <p>
 * This plugin reads Jandex indexes from all dependencies and generates AOT
 * context classes at compile time. It has full classpath access, solving the
 * annotation processor isolation problem.
 * </p>
 */
@Mojo(name = "generate-aot", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE, requiresProject = true)
public class SummerMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
	private File outputDirectory;

	private static final String BEANS_SUFFIX = " beans";

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("[Summer] Starting AOT code generation...");

		try {
			// 1. Load all Jandex indexes from dependencies
			CompositeIndex index = loadIndexes();
			getLog().info("[Summer] Loaded Jandex index with " + index.getKnownClasses().size() + " classes");

			// 2. Discover beans from the index
			List<BeanDefinition> beans = BeanDiscovery.discoverBeans(index, null);
			getLog().info("[Summer] Discovered " + beans.size() + BEANS_SUFFIX);

			// 3. Evaluate @ConditionalOnBean conditions
			ConditionalEvaluator.evaluate(beans, index);
			getLog().info("[Summer] After conditional evaluation: " + beans.size() + BEANS_SUFFIX);

			// 4. Resolve dependencies
			DependencyResolver resolver = new DependencyResolver();
			List<BeanDefinition> sorted = resolver.resolve(beans);
			getLog().info("[Summer] Resolved dependencies for " + sorted.size() + BEANS_SUFFIX);

			// 5. Generate AOT code
			File generatedDir = new File(project.getBasedir(), "target/generated-sources/aot");
			generatedDir.mkdirs();

			new AotContextGenerator().generate(sorted, generatedDir);
			new AotProxyGenerator().generate(sorted, generatedDir);
			new RouteAdapterGenerator().generate(sorted, generatedDir);
			new RowMapperGenerator().generate(index, generatedDir);

			// 6. Compile generated sources
			compileGeneratedSources(generatedDir);

			getLog().info("[Summer] AOT code generation complete");

		} catch (Exception e) {
			throw new MojoExecutionException("[Summer] AOT generation failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Compile generated Java source files using the project's classpath.
	 */
	private void compileGeneratedSources(File generatedDir) throws Exception {
		java.util.List<File> sourceFiles = new java.util.ArrayList<>();
		collectJavaFiles(generatedDir, sourceFiles);
		if (sourceFiles.isEmpty()) {
			return;
		}

		getLog().info("[Summer] Compiling " + sourceFiles.size() + " generated source(s)");

		// Build classpath from project dependencies
		java.util.List<String> classpathEntries = new java.util.ArrayList<>();
		classpathEntries.add(outputDirectory.getAbsolutePath());
		for (Object obj : project.getArtifacts()) {
			Artifact artifact = (Artifact) obj;
			if (artifact.getFile() != null) {
				classpathEntries.add(artifact.getFile().getAbsolutePath());
			}
		}
		String classpath = String.join(System.getProperty("path.separator"), classpathEntries);

		// Prepare output directory for compiled classes
		File compileOutputDir = new File(project.getBuild().getDirectory(), "classes");
		compileOutputDir.mkdirs();

		// Build javac command
		java.util.List<String> command = new java.util.ArrayList<>();
		command.add("javac");
		command.add("-cp");
		command.add(classpath);
		command.add("-d");
		command.add(compileOutputDir.getAbsolutePath());
		command.add("--release");
		command.add("25");
		for (File f : sourceFiles) {
			command.add(f.getAbsolutePath());
		}

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.inheritIO();
		Process process = pb.start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new MojoExecutionException(
					"[Summer] Compilation of generated sources failed with exit code " + exitCode);
		}
	}

	private void collectJavaFiles(File dir, java.util.List<File> result) {
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
	 * Load Jandex indexes from all dependency JARs and the current project's
	 * output.
	 */
	private CompositeIndex loadIndexes() throws IOException {
		List<IndexView> indexes = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		// 1. Load from current project's output directory
		loadFromDirectory(outputDirectory, indexes, seen);

		// 2. Load from all dependency JARs
		for (Object obj : project.getArtifacts()) {
			Artifact artifact = (Artifact) obj;
			File file = artifact.getFile();
			if (file == null || !file.exists()) {
				continue;
			}

			if (file.isDirectory()) {
				loadFromDirectory(file, indexes, seen);
			} else if (file.getName().endsWith(".jar")) {
				loadFromJar(file, indexes, seen);
			}
		}

		if (indexes.isEmpty()) {
			getLog().warn("[Summer] No Jandex indexes found. Ensure dependencies have jandex-maven-plugin configured.");
			return CompositeIndex.create(new ArrayList<>());
		}

		return CompositeIndex.create(indexes);
	}

	private void loadFromDirectory(File dir, List<IndexView> indexes, Set<String> seen) throws IOException {
		Path indexPath = dir.toPath().resolve("META-INF").resolve("jandex.idx");
		if (Files.exists(indexPath) && seen.add(indexPath.toString())) {
			try (InputStream is = Files.newInputStream(indexPath)) {
				indexes.add(new IndexReader(is).read());
				getLog().debug("[Summer] Loaded index from directory: " + indexPath);
			}
		}
	}

	private void loadFromJar(File file, List<IndexView> indexes, Set<String> seen) {
		try (java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
			java.util.jar.JarEntry entry = jar.getJarEntry("META-INF/jandex.idx");
			if (entry != null && seen.add(file.getAbsolutePath())) {
				try (InputStream is = jar.getInputStream(entry)) {
					indexes.add(new IndexReader(is).read());
					getLog().debug("[Summer] Loaded index from JAR: " + file.getName());
				}
			}
		} catch (Exception e) {
			getLog().warn("[Summer] Failed to read index from " + file.getName() + ": " + e.getMessage());
		}
	}
}
