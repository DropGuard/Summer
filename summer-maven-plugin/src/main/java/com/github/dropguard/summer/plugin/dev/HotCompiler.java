package com.github.dropguard.summer.plugin.dev;

import com.github.dropguard.summer.aot.AotSourceCompiler;
import java.io.File;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/** Executes incremental fast-compilation via the standard JavaCompiler API. */
public class HotCompiler {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(HotCompiler.class);
    final String classpath;
    final File outputDir;

    public HotCompiler(String classpath, File outputDir) {
        this.classpath = classpath;
        this.outputDir = outputDir;
    }

    /**
     * Compiles specific java files into the target output directory.
     *
     * @param sourceFiles the list of .java files that changed
     * @return true if compilation succeeds, false otherwise
     */
    public boolean compile(List<File> sourceFiles) {
        if (sourceFiles.isEmpty()) return true;

        List<String> options =
                List.of(
                        "-classpath",
                        classpath,
                        "-d",
                        outputDir.getAbsolutePath(),
                        "-g",
                        "-parameters");

        log.info("[Summer] Recompiling " + sourceFiles.size() + " changed file(s)...");
        try {
            List<Diagnostic<? extends JavaFileObject>> diags =
                    AotSourceCompiler.compile(sourceFiles, options);

            if (diags.isEmpty()) {
                log.info("[Summer] Compilation successful.");
                return true;
            }

            for (var diag : diags) {
                log.error("[Summer] " + diag);
            }
            log.error("[Summer] Compilation failed ({} diagnostic(s)).", diags.size());
            return false;
        } catch (Exception e) {
            log.error("[Summer] Compilation failed", e);
            return false;
        }
    }
}
