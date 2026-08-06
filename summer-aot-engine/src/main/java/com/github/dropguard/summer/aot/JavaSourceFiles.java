package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import java.io.File;
import java.util.List;

/**
 * Collects generated {@code .java} sources under a directory, recursively.
 *
 * <p>Shared by the AOT engine's test-time compile path and the Maven plugin's production compile
 * path (previously duplicated verbatim in {@link AotEngine} and {@code SummerMojo}).
 */
@Internal
public final class JavaSourceFiles {

    private JavaSourceFiles() {}

    /**
     * Appends every {@code .java} file under {@code dir} (recursively) to {@code result}.
     *
     * @param dir the directory to scan (missing/unreadable directories yield nothing)
     * @param result the accumulator list
     */
    public static void collect(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                collect(f, result);
            } else if (f.getName().endsWith(".java")) {
                result.add(f);
            }
        }
    }
}
