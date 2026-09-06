package com.github.dropguard.summer.plugin.dev;

import com.github.dropguard.summer.aot.AotSourceCompiler;
import java.io.File;
import java.nio.file.Path;
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

    /**
     * Removes the class files a deleted/renamed source left behind ({@code Foo.class} and nested
     * {@code Foo$*.class}) so the dev child stops serving a ghost bean. The deletion is scoped to
     * the SOURCE ROOT's package directory ({@code com/a/Util.java} prunes only {@code
     * com/a/Util*.class}) — matching by simple name across the whole output dir would also nuke an
     * unrelated {@code com/b/Util.class}.
     *
     * @param sourceRoot the java source root the deleted file lived under ({@code src/main/java})
     * @param deletedSource the deleted/renamed source file
     * @return how many class files were removed
     */
    int pruneStaleClasses(File sourceRoot, File deletedSource) {
        if (sourceRoot == null) {
            return 0;
        }
        Path deleted = deletedSource.toPath().normalize();
        Path root = sourceRoot.toPath().normalize();
        Path packagePath;
        try {
            packagePath = root.relativize(deleted).getParent();
        } catch (IllegalArgumentException e) {
            log.warn(
                    "[Summer] Deleted source "
                            + deleted
                            + " is not under the watched source root "
                            + root
                            + " — skipping stale-class prune");
            return 0;
        }
        String name = deletedSource.getName();
        String className = name.substring(0, name.lastIndexOf('.'));
        Path pkgDir =
                packagePath == null ? outputDir.toPath() : outputDir.toPath().resolve(packagePath);
        final int[] removed = {0};
        try (java.util.stream.Stream<java.nio.file.Path> list = java.nio.file.Files.list(pkgDir)) {
            list.filter(
                            p -> {
                                String n = p.getFileName().toString();
                                return (n.equals(className + ".class")
                                        || (n.startsWith(className + "$") && n.endsWith(".class")));
                            })
                    .forEach(
                            p -> {
                                try {
                                    java.nio.file.Files.deleteIfExists(p);
                                    removed[0]++;
                                    log.info(
                                            "[Summer] Pruned stale class "
                                                    + outputDir.toPath().relativize(p));
                                } catch (Exception e) {
                                    log.warn("[Summer] Failed to prune " + p, e);
                                }
                            });
        } catch (java.nio.file.NoSuchFileException e) {
            // Nothing compiled under that package yet — nothing to prune.
        } catch (Exception e) {
            log.warn("[Summer] Failed to prune stale classes of " + className, e);
        }
        return removed[0];
    }
}
