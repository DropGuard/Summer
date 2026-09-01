package com.github.dropguard.summer.plugin;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Parses Java source files with javac to extract top-level and nested class binary names, then
 * reconciles the result against the on-disk class files in {@code target/classes}.
 *
 * <p>This replaces the hand-written lexer in {@code StaleClassState}. javac is the single source of
 * truth for "what classes does this source define", which means the extraction stays correct as the
 * Java grammar evolves (records, sealed, pattern-matching declarations, ...).
 *
 * <p>The state file ({@code target/summer/source-classes.tsv}) stores the last successful snapshot
 * of {@code source path → binary class names}, so a deleted source file can be identified on the
 * next run even when the source itself is gone. Reading the state when missing or corrupt returns
 * an empty map — the caller proceeds with a full re-parse, which is the defined fallback behavior.
 */
final class SummerSourceIndex {

    private SummerSourceIndex() {}

    private static final String STATE_RELATIVE_PATH = "summer/source-classes.tsv";
    private static final String STATE_HEADER = "# Summer source/class state v1";

    // ── Public API ───────────────────────────────────────────────────────────────

    /**
     * Parses every {@code .java} file under the given source roots and returns the full set of
     * binary class names (e.g. {@code "com.example.Foo"}, {@code "com.example.Foo$Inner"}).
     *
     * <p>Includes nested types declared inside any top-level type: regular nested classes, records,
     * enums, annotation types, sealed hierarchies. Local and anonymous classes are excluded because
     * they have no stable binary name at the source level.
     *
     * <p>{@code package-info.java} files have no top-level type and contribute nothing.
     *
     * <p>Sources with syntax errors contribute whatever types the parser could recover. Other
     * sources are unaffected. Per-file attribution failures do not abort the walk.
     *
     * @param sourceRoots directories to walk for {@code .java} files
     * @return unmodifiable set of binary class names
     */
    static Set<String> parseSources(List<File> sourceRoots) throws IOException {
        Set<String> result = new HashSet<>();
        if (sourceRoots == null) return Collections.emptySet();
        List<File> sources = new ArrayList<>();
        for (File root : sourceRoots) {
            if (root != null && root.isDirectory()) collectJavaFiles(root, sources);
        }
        if (sources.isEmpty()) return Collections.emptySet();
        result.addAll(parseWithJavac(sources));
        return Collections.unmodifiableSet(result);
    }

    /**
     * Deletes every {@code .class} file inside {@code outputDirectory} whose binary name is not
     * present in {@code sourceClassNames}. The {@code META-INF} directory is preserved
     * unconditionally (Jandex index, manifest, services descriptors).
     *
     * <p>Deletion is best-effort: a single missing class file cannot abort the whole pass.
     *
     * @param outputDirectory the directory to reconcile (typically {@code target/classes})
     * @param sourceClassNames binary names that should exist on disk
     * @return the number of class files deleted
     */
    static int reconcile(File outputDirectory, Set<String> sourceClassNames) throws IOException {
        if (outputDirectory == null || !outputDirectory.isDirectory()) return 0;
        int removed = 0;
        Set<String> allowed = new HashSet<>(sourceClassNames);
        try (var stream = Files.walk(outputDirectory.toPath())) {
            List<Path> paths = stream.filter(Files::isRegularFile).toList();
            for (Path path : paths) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".class")) continue;
                Path relative = outputDirectory.toPath().relativize(path);
                if (relative.startsWith("META-INF")) continue;
                String binary = toBinaryName(relative.toString());
                if (!isAllowed(binary, allowed)) {
                    try {
                        Files.deleteIfExists(path);
                        removed++;
                    } catch (IOException ignore) {
                        // best-effort
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Determines whether a binary class name is allowed to exist on disk.
     *
     * <p>Direct members (top-level and nested classes) must be explicitly present in {@code
     * allowed}. Local and anonymous classes (e.g. {@code Foo$1}, {@code Foo$1Req}, {@code
     * Foo$Inner$1}) are named by javac with a {@code $} followed by digits; they are permitted as
     * long as their enclosing class exists in {@code allowed}.
     */
    static boolean isAllowed(String binary, Set<String> allowed) {
        if (allowed.contains(binary)) {
            return true;
        }
        String current = binary;
        while (true) {
            int lastDollar = current.lastIndexOf('$');
            if (lastDollar < 0) {
                return false;
            }
            if (lastDollar + 1 < current.length()
                    && Character.isDigit(current.charAt(lastDollar + 1))) {
                current = current.substring(0, lastDollar);
                if (allowed.contains(current)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    /**
     * Writes the current source snapshot to the state file inside {@code basedir/target/}.
     *
     * <p>Format: {@code HEADER\n<sourcePath>\t<class1,class2,...>\n...}, one line per source.
     *
     * @param basedir the Maven project basedir
     * @param sources map from source path to its class names
     */
    static void writeSnapshot(File basedir, Map<String, List<String>> sources) throws IOException {
        Path stateFile = stateFile(basedir);
        Files.createDirectories(stateFile.getParent());

        StringBuilder content = new StringBuilder(STATE_HEADER).append('\n');
        for (Map.Entry<String, List<String>> entry : sources.entrySet()) {
            content.append(entry.getKey()).append('\t');
            List<String> classes = entry.getValue();
            for (int i = 0; i < classes.size(); i++) {
                if (i > 0) content.append(',');
                content.append(classes.get(i));
            }
            content.append('\n');
        }

        Path temp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
        Files.writeString(
                temp,
                content.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            Files.move(
                    temp,
                    stateFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Reads the snapshot previously written by {@link #writeSnapshot}. Returns an empty map if the
     * state file does not exist, is unreadable, or is corrupt — callers MUST treat this as a safe
     * fallback and proceed with a full re-parse.
     *
     * @param basedir the Maven project basedir
     * @return snapshot, or empty map on any I/O error
     */
    static Map<String, List<String>> readSnapshot(File basedir) {
        Path stateFile = stateFile(basedir);
        if (!Files.exists(stateFile)) return Collections.emptyMap();
        Map<String, List<String>> result = new HashMap<>();
        try {
            for (String line : Files.readAllLines(stateFile, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String path = line.substring(0, tab);
                String classesField = line.substring(tab + 1);
                List<String> classes = new ArrayList<>();
                if (!classesField.isEmpty()) {
                    Collections.addAll(classes, classesField.split(",", -1));
                }
                result.put(path, Collections.unmodifiableList(classes));
            }
        } catch (IOException e) {
            return Collections.emptyMap();
        }
        return result;
    }

    // ── Internal: javac AST extraction (public API only) ─────────────────────────

    /**
     * Drives the system compiler's {@link JavacTask#parse()} phase. The returned {@link
     * JavaCompiler.CompilationTask} is the SPI implementation ({@code JavacTaskImpl}) and
     * implements {@link JavacTask} — the official public API in {@code com.sun.source.util},
     * exported by the {@code jdk.compiler} module. We downcast in one place and stay on the public
     * tree API ({@link CompilationUnitTree}, {@link ClassTree}) from there on.
     *
     * <p>Parse-only is dramatically faster than a full compile (≈7ms vs ≈300ms on a 50-source
     * fixture), and is sufficient to enumerate top-level + nested types — which is all we need.
     *
     * <p>Syntax errors on individual sources do not abort the walk; the parser recovers and yields
     * whatever types it could identify. The diagnostics stream receives the error reports but we
     * don't surface them — class-name extraction is the only contract here.
     */
    private static Set<String> parseWithJavac(List<File> sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("No system Java compiler available; run on a JDK, not a JRE.");
        }

        Set<String> names = new HashSet<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends javax.tools.JavaFileObject> units =
                    fm.getJavaFileObjectsFromFiles(sources);
            // -sourcepath so cross-file references (other types in the same source set) resolve.
            // We pass the parent directory of the deepest source — the directories themselves
            // are walked by collectJavaFiles, and their common ancestor is the sourcepath root.
            String sourcepath = inferSourcepath(sources);
            List<String> options =
                    sourcepath == null
                            ? List.of("-proc:none", "-implicit:none")
                            : List.of("-proc:none", "-implicit:none", "-sourcepath", sourcepath);
            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fm, null, options, null, units);
            if (!(task instanceof JavacTask javacTask)) {
                throw new IOException(
                        "System compiler does not implement com.sun.source.util.JavacTask"
                                + " (got "
                                + task.getClass().getName()
                                + ")."
                                + " Run on a standard JDK 9+ with javac.");
            }
            for (CompilationUnitTree unit : javacTask.parse()) {
                String pkg = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
                for (Tree def : unit.getTypeDecls()) {
                    if (def instanceof ClassTree top) {
                        String topName = top.getSimpleName().toString();
                        String topBinary = pkg.isEmpty() ? topName : pkg + "." + topName;
                        names.add(topBinary);
                        collectNested(top, topBinary, names);
                    }
                }
            }
        }
        return names;
    }

    /**
     * Computes the smallest common ancestor directory of all sources — that becomes the sourcepath
     * root, so cross-file references between sources resolve.
     */
    private static String inferSourcepath(List<File> sources) {
        if (sources.isEmpty()) return null;
        File common = sources.get(0).getAbsoluteFile();
        for (int i = 1; i < sources.size(); i++) {
            common = commonAncestor(common, sources.get(i).getAbsoluteFile());
            if (common == null) return null;
        }
        return common.getAbsolutePath();
    }

    private static File commonAncestor(File a, File b) {
        // Find the longest shared parent path. Walk up from the deeper one.
        List<File> aParts = new ArrayList<>();
        for (File c = a; c != null; c = c.getParentFile()) aParts.add(c);
        Set<File> aSet = new HashSet<>(aParts);
        for (File c = b; c != null; c = c.getParentFile()) {
            if (aSet.contains(c)) return c;
        }
        return null;
    }

    /** Recursively collects nested class binary names under an enclosing class declaration. */
    private static void collectNested(
            ClassTree enclosing, String enclosingBinary, Set<String> names) {
        for (Tree member : enclosing.getMembers()) {
            if (member instanceof ClassTree nested) {
                String nestedBinary = enclosingBinary + "$" + nested.getSimpleName();
                names.add(nestedBinary);
                collectNested(nested, nestedBinary, names);
            }
        }
    }

    // ── Internal: filesystem utilities ───────────────────────────────────────────

    private static Path stateFile(File basedir) {
        return basedir.toPath().resolve("target").resolve(STATE_RELATIVE_PATH);
    }

    private static void collectJavaFiles(File root, List<File> acc) {
        File[] files = root.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectJavaFiles(f, acc);
            } else if (f.getName().endsWith(".java")) {
                acc.add(f);
            }
        }
    }

    private static String toBinaryName(String classFilePath) {
        String withoutExt = classFilePath;
        if (withoutExt.endsWith(".class")) {
            withoutExt = withoutExt.substring(0, withoutExt.length() - 6);
        }
        return withoutExt.replace('/', '.').replace('\\', '.');
    }
}
