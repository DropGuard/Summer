package summer.tck.compiler;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.*;
import summer.compiler.SummerProcessor;

public class MemoryCompiler {

	private final Path tempOutputDir;

	public MemoryCompiler() {
		try {
			this.tempOutputDir = Files.createTempDirectory("summer-tck-aot-");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public ClassLoader compileAndLoad(List<File> sourceFiles) {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException(
					"System JavaCompiler is not available. Ensure you are running with a JDK, not a JRE.");
		}

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {

			// Set output directory
			fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(tempOutputDir.toFile()));
			fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(tempOutputDir.toFile()));

			Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);

			JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, // options
					null, // classes to be processed
					compilationUnits);

			// Register our SummerProcessor
			task.setProcessors(List.of(new SummerProcessor()));

			boolean success = task.call();

			if (!success) {
				StringBuilder sb = new StringBuilder("Compilation failed:\n");
				for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
					sb.append(diagnostic.toString()).append("\n");
				}

				// Print ALL generated source code to help debugging (not just .java)
				try {
					Files.walk(tempOutputDir).filter(p -> p.toString().endsWith(".java")).forEach(p -> {
						try {
							sb.append("\n--- ").append(p.getFileName()).append(" ---\n");
							sb.append(Files.readString(p));
							sb.append("\n-----------------------\n");
						} catch (IOException e) {
							// Ignore
						}
					});
				} catch (IOException e) {
					// Ignore
				}

				throw new RuntimeException(sb.toString());
			}

			// Create and return a classloader that includes the compiled output
			URL outputUrl = tempOutputDir.toUri().toURL();
			return new URLClassLoader(new URL[]{outputUrl}, this.getClass().getClassLoader());

		} catch (IOException e) {
			throw new RuntimeException("Error during compilation", e);
		}
	}

	public void cleanUp() {
		try {
			Files.walk(tempOutputDir).map(Path::toFile).forEach(File::delete);
		} catch (IOException e) {
			// Ignore cleanup errors
		}
	}
}
