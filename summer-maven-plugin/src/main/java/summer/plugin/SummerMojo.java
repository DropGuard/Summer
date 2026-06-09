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
			if (index.getKnownClasses().isEmpty()) {
				getLog().info("[Summer] No Jandex index found, skipping AOT generation");
				return;
			}
			getLog().info("[Summer] Loaded Jandex index with " + index.getKnownClasses().size() + " classes");

			// 2. Clean stale generated sources to prevent stale class pollution
			File generatedDir = new File(project.getBasedir(), "target/generated-sources/aot");
			if (generatedDir.exists()) {
				java.nio.file.Files.walk(generatedDir.toPath()).sorted(java.util.Comparator.reverseOrder())
						.map(java.nio.file.Path::toFile).forEach(File::delete);
				getLog().info("[Summer] Cleaned stale AOT generated sources");
			}
			generatedDir.mkdirs();

			// 3. Generate RowMapper classes (must happen before BeanDiscovery
			// so the generated RowMapperConfiguration is in the index)
			new RowMapperGenerator().generate(index, generatedDir);

			// 4. Compile generated sources and re-index
			compileGeneratedSources(generatedDir);
			index = reloadIndex(index, generatedDir);

			// 5. Discover beans from the index (includes condition evaluation)
			List<BeanDefinition> beans = new BeanDiscovery(index).discover(null);
			getLog().info("[Summer] Discovered " + beans.size() + BEANS_SUFFIX);

			// 6. Resolve dependencies
			DependencyResolver resolver = new DependencyResolver();
			List<BeanDefinition> sorted = resolver.resolve(beans);
			getLog().info("[Summer] Resolved dependencies for " + sorted.size() + BEANS_SUFFIX);

			// 7. Generate AOT context and proxies
			new AotContextGenerator().generate(sorted, generatedDir, index);
			new AotProxyGenerator().generate(sorted, index, generatedDir);
			new RouteAdapterGenerator().generate(sorted, generatedDir);

			// 8. Compile all generated sources
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

		javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new MojoExecutionException("[Summer] No Java compiler available. Ensure a JDK is installed.");
		}

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

		// Collect source file paths
		java.util.List<String> sourcePaths = sourceFiles.stream().map(File::getAbsolutePath).toList();

		javax.tools.StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		Iterable<? extends javax.tools.JavaFileObject> compilationUnits = fileManager
				.getJavaFileObjectsFromStrings(sourcePaths);

		java.util.List<String> options = java.util.List.of("-cp", classpath, "-d", compileOutputDir.getAbsolutePath());

		javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null,
				compilationUnits);

		if (!task.call()) {
			throw new MojoExecutionException("[Summer] Compilation of generated sources failed");
		}
		fileManager.close();
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
	 * Re-indexes compiled classes from the generated sources directory and merges
	 * with the existing index so that newly generated classes (e.g.
	 * RowMapperConfiguration) are visible to BeanDiscovery.
	 */
	private CompositeIndex reloadIndex(CompositeIndex existing, File generatedDir) throws IOException {
		File compileOutputDir = new File(project.getBuild().getDirectory(), "classes");
		org.jboss.jandex.Indexer indexer = new org.jboss.jandex.Indexer();
		int count = 0;
		if (compileOutputDir.isDirectory()) {
			for (File classFile : collectClassFiles(compileOutputDir)) {
				try (java.io.InputStream is = new java.io.FileInputStream(classFile)) {
					indexer.index(is);
					count++;
				}
			}
		}
		if (count == 0) {
			return existing;
		}
		getLog().info("[Summer] Re-indexed " + count + " compiled class(es)");
		List<IndexView> all = new ArrayList<>();
		all.add(existing);
		all.add(indexer.complete());
		return CompositeIndex.create(all);
	}

	private java.util.List<File> collectClassFiles(File dir) {
		java.util.List<File> result = new java.util.ArrayList<>();
		File[] files = dir.listFiles();
		if (files == null)
			return result;
		for (File f : files) {
			if (f.isDirectory()) {
				result.addAll(collectClassFiles(f));
			} else if (f.getName().endsWith(".class")) {
				result.add(f);
			}
		}
		return result;
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
