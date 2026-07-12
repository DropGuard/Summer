package summer.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.apache.maven.plugin.logging.Log;

/**
 * Executes incremental fast-compilation via the standard JavaCompiler API.
 */
public class HotCompiler {
	private final Log log;
	final String classpath;
	final File outputDir;

	public HotCompiler(Log log, String classpath, File outputDir) {
		this.log = log;
		this.classpath = classpath;
		this.outputDir = outputDir;
	}

	/**
	 * Compiles specific java files into the target output directory.
	 * 
	 * @param sourceFiles
	 *            the list of .java files that changed
	 * @return true if compilation succeeds, false otherwise
	 */
	public boolean compile(List<File> sourceFiles) {
		if (sourceFiles.isEmpty())
			return true;

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			log.error("System JavaCompiler not found. Please run with a JDK, not a JRE.");
			return false;
		}

		List<String> options = new ArrayList<>(
				Arrays.asList("-classpath", classpath, "-d", outputDir.getAbsolutePath(), "-g", "-parameters"));

		List<String> filePaths = new ArrayList<>();
		for (File f : sourceFiles) {
			filePaths.add(f.getAbsolutePath());
		}

		List<String> args = new ArrayList<>(options);
		args.addAll(filePaths);

		log.info("[Summer] Recompiling " + sourceFiles.size() + " changed file(s)...");
		int result = compiler.run(null, null, null, args.toArray(new String[0]));

		if (result == 0) {
			log.info("[Summer] Compilation successful.");
			return true;
		} else {
			log.error("[Summer] Compilation failed.");
			return false;
		}
	}
}
