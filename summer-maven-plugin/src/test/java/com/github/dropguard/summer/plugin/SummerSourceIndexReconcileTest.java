package com.github.dropguard.summer.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reconcile must not delete classes it cannot derive: a foreign source's synthetic nested classes
 * ({@code Foo$Companion}, {@code Foo$_run_closure1}) are live code — the path-based derivation only
 * sees the top level, so reconcile tolerates any {@code $}-descendant of a foreign top-level name
 * while still sweeping genuinely stale classes. The pre-fix behavior deleted them, and the jar
 * failed at runtime with {@code NoClassDefFoundError}.
 */
class SummerSourceIndexReconcileTest {

    @TempDir Path temp;

    @Test
    void foreignNestedClassesSurviveReconcile() throws IOException {
        Path sources = Files.createDirectories(temp.resolve("src"));
        Path classes = Files.createDirectories(temp.resolve("classes"));
        write(sources, "Foo.kt");
        write(sources, "Bar.java");
        for (String name :
                new String[] {
                    "Foo.class",
                    "Foo$Companion.class",
                    "Foo$_run_closure1.class",
                    "Bar.class",
                    "Bar$1.class",
                    "Stale.class"
                }) {
            write(classes, name);
        }

        List<File> roots = List.of(sources.toFile());
        Set<String> derived = SummerSourceIndex.collectPathDerivedBinaryNames(roots);
        int removed =
                SummerSourceIndex.reconcile(
                        classes.toFile(),
                        derived,
                        SummerSourceIndex.collectForeignTopLevelNames(roots));

        assertEquals(1, removed, "only Stale.class (no source at all) is stale");
        assertTrue(has(classes, "Foo.class"));
        assertTrue(has(classes, "Foo$Companion.class"), "Kotlin companion must survive");
        assertTrue(has(classes, "Foo$_run_closure1.class"), "Groovy closure must survive");
        assertTrue(has(classes, "Bar.class"));
        assertTrue(has(classes, "Bar$1.class"), "javac local class must survive");
        assertFalse(has(classes, "Stale.class"));
    }

    @Test
    void javaNestedClassesWithoutAJavacParseStillTolerateAnonymousDescendants() {
        // Direct regression of the $digit tolerance: an allowed enclosing Java class permits its
        // anonymous/local $digit descendants, but not named nested classes (the javac parse
        // provides those exactly).
        Set<String> allowed = Set.of("com.x.Bar");
        Set<String> noForeign = Set.of();
        assertTrue(SummerSourceIndex.isAllowed("com.x.Bar$1", allowed, noForeign));
        assertFalse(SummerSourceIndex.isAllowed("com.x.Bar$Inner", allowed, noForeign));
        assertTrue(
                SummerSourceIndex.isAllowed(
                        "com.x.Foo$Companion", Set.of("com.x.Bar"), Set.of("com.x.Foo")));
        assertFalse(
                SummerSourceIndex.isAllowed(
                        "com.x.Orphan$1", Set.of("com.x.Bar"), Set.of("com.x.Foo")));
    }

    private static void write(Path dir, String name) throws IOException {
        Files.createDirectories(dir.getParent());
        Files.writeString(dir.resolve(name), "// placeholder");
    }

    private static boolean has(Path classes, String name) {
        return Files.exists(classes.resolve(name));
    }
}
