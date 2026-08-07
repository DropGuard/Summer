package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

/**
 * Single implementation of the javac invocation shared by the test-time {@link AotEngine} and the
 * build-time {@code summer-maven-plugin} mojo — compiler lookup, file manager, source units,
 * diagnostics collection, and task invocation all live here, so neither caller re-implements the
 * ceremony. The two callers differ only in the classpath, output directory, and extra options they
 * pass.
 */
@Internal
public final class AotSourceCompiler {

    private AotSourceCompiler() {}

    /**
     * Compiles the given source files with the given javac options (classpath, output directory,
     * {@code --release}, ...).
     *
     * @return the javac diagnostics when compilation failed; an empty list when it succeeded
     * @throws IllegalStateException when no system Java compiler is available (a JRE, not a JDK)
     */
    public static List<Diagnostic<? extends JavaFileObject>> compile(
            List<File> sourceFiles, List<String> options) throws IOException {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "No system Java compiler available. Ensure the project runs on a JDK, not a"
                            + " JRE.");
        }
        var fm = compiler.getStandardFileManager(null, null, null);
        var units =
                fm.getJavaFileObjectsFromStrings(
                        sourceFiles.stream().map(File::getAbsolutePath).toList());
        var diags = new DiagnosticCollector<JavaFileObject>();
        var task = compiler.getTask(null, fm, diags, options, null, units);
        boolean ok = task.call();
        fm.close();
        return ok ? List.of() : diags.getDiagnostics();
    }
}
