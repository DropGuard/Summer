package com.github.dropguard.summer.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link SummerSourceIndex}.
 *
 * <p>Strategy: write Java source strings to a temp directory and exercise the four public methods.
 * No real Maven build runs — the test constrains the parsing + reconciliation contract in
 * isolation, the way it will run inside {@code SummerMojo}.
 */
class SummerSourceIndexTest {

    // ── parseSources ─────────────────────────────────────────────────────────────

    @Test
    void parsesSingleTopLevelClass(@TempDir Path temp) throws Exception {
        writeSource(temp, "com/example/Foo.java", "package com.example; class Foo {}");
        Set<String> result = SummerSourceIndex.parseSources(List.of(temp.toFile()));
        assertEquals(Set.of("com.example.Foo"), result);
    }

    @Test
    void parsesMultipleTopLevelClassesInOneFile(@TempDir Path temp) throws Exception {
        writeSource(
                temp,
                "com/example/Multi.java",
                "package com.example; class A {} class B {} interface C {}");
        Set<String> result = SummerSourceIndex.parseSources(List.of(temp.toFile()));
        assertEquals(Set.of("com.example.A", "com.example.B", "com.example.C"), result);
    }

    @Test
    void parsesNestedClassesWithBinaryNaming(@TempDir Path temp) throws Exception {
        String src =
                "package com.example;\n"
                        + "class Outer {\n"
                        + "  static class Inner {}\n"
                        + "  record Rec(int x) {}\n"
                        + "  enum En { ONE, TWO }\n"
                        + "  @interface Ann {}\n"
                        + "}\n"
                        + "sealed class Sealed permits Sub1, Sub2 {}\n"
                        + "final class Sub1 extends Sealed {}\n"
                        + "final class Sub2 extends Sealed {}\n";
        writeSource(temp, "com/example/Outer.java", src);
        Set<String> result = SummerSourceIndex.parseSources(List.of(temp.toFile()));
        assertTrue(result.contains("com.example.Outer"));
        assertTrue(result.contains("com.example.Outer$Inner"));
        assertTrue(result.contains("com.example.Outer$Rec"));
        assertTrue(result.contains("com.example.Outer$En"));
        assertTrue(result.contains("com.example.Outer$Ann"));
        assertTrue(result.contains("com.example.Sealed"));
        assertTrue(result.contains("com.example.Sub1"));
        assertTrue(result.contains("com.example.Sub2"));
        assertEquals(8, result.size());
    }

    @Test
    void parsesDeeplyNestedClasses(@TempDir Path temp) throws Exception {
        String src =
                "package com.example;\n"
                        + "class A {\n"
                        + "  static class B {\n"
                        + "    static class C {\n"
                        + "      record D(int x) {}\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n";
        writeSource(temp, "com/example/A.java", src);
        Set<String> result = SummerSourceIndex.parseSources(List.of(temp.toFile()));
        assertEquals(
                Set.of(
                        "com.example.A",
                        "com.example.A$B",
                        "com.example.A$B$C",
                        "com.example.A$B$C$D"),
                result);
    }

    @Test
    void ignoresPackageInfo(@TempDir Path temp) throws Exception {
        writeSource(temp, "com/example/package-info.java", "@Deprecated package com.example;");
        writeSource(temp, "com/example/Real.java", "package com.example; class Real {}");
        Set<String> result = SummerSourceIndex.parseSources(List.of(temp.toFile()));
        assertEquals(Set.of("com.example.Real"), result);
    }

    @Test
    void walksMultipleSourceRoots(@TempDir Path temp) throws Exception {
        Path src1 = temp.resolve("src1");
        Path src2 = temp.resolve("src2");
        Files.createDirectories(src1.resolve("com/example"));
        Files.createDirectories(src2.resolve("com/example"));
        Files.writeString(
                src1.resolve("com/example/Foo.java").toFile().toPath(),
                "package com.example; class Foo {}",
                StandardCharsets.UTF_8);
        Files.writeString(
                src2.resolve("com/example/Bar.java").toFile().toPath(),
                "package com.example; class Bar {}",
                StandardCharsets.UTF_8);
        Set<String> result = SummerSourceIndex.parseSources(List.of(src1.toFile(), src2.toFile()));
        assertEquals(Set.of("com.example.Foo", "com.example.Bar"), result);
    }

    @Test
    void emptyRootsReturnEmptySet(@TempDir Path temp) throws Exception {
        Set<String> result = SummerSourceIndex.parseSources(List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void nullRootsAreTolerated(@TempDir Path temp) throws Exception {
        Set<String> result = SummerSourceIndex.parseSources(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void ignoresSourcesWithSyntaxErrors(@TempDir Path temp) throws Exception {
        // A source that fails to attribute still contributes the types that DID parse.
        writeSource(temp, "com/example/Good.java", "package com.example; class Good {}");
        writeSource(
                temp, "com/example/Broken.java", "package com.example; class Broken { void m( }");
        // parse() phase must not abort on syntax errors — Good still contributes itself,
        // and Broken may or may not depending on parser recovery (we don't constrain that).
        // The contract is just: no exception is thrown, Good is always present.
        Set<String> result = SummerSourceIndex.parseSources(List.of(temp.toFile()));
        assertTrue(result.contains("com.example.Good"));
    }

    // ── reconcile ────────────────────────────────────────────────────────────────

    @Test
    void reconcileRemovesClassNotInSourceSet(@TempDir Path temp) throws Exception {
        File classes = temp.resolve("classes").toFile();
        classes.mkdirs();
        touchClass(classes, "com/example/Alive.class");
        File stale = touchClass(classes, "com/example/Gone.class");

        int removed = SummerSourceIndex.reconcile(classes, Set.of("com.example.Alive"));

        assertEquals(1, removed);
        assertFalse(stale.exists());
        assertTrue(new File(classes, "com/example/Alive.class").exists());
    }

    @Test
    void reconcilePreservesMetaInf(@TempDir Path temp) throws Exception {
        File classes = temp.resolve("classes").toFile();
        classes.mkdirs();
        File metaInf = new File(classes, "META-INF");
        metaInf.mkdirs();
        File idx = touchClass(metaInf, "jandex.idx");
        // Add a .class file under META-INF that the source set does not include; it must
        // survive because META-INF is unconditionally preserved.
        File stray = touchClass(metaInf, "some-service.class");

        int removed = SummerSourceIndex.reconcile(classes, Set.of());

        assertEquals(0, removed);
        assertTrue(idx.exists());
        assertTrue(stray.exists());
    }

    @Test
    void reconcileLeavesNonClassFilesAlone(@TempDir Path temp) throws Exception {
        File classes = temp.resolve("classes").toFile();
        classes.mkdirs();
        File yml = new File(classes, "application.yml");
        Files.writeString(yml.toPath(), "name: x\n", StandardCharsets.UTF_8);

        int removed = SummerSourceIndex.reconcile(classes, Set.of());

        assertEquals(0, removed);
        assertTrue(yml.exists());
    }

    @Test
    void reconcileAcceptsNestedClassBinaryNames(@TempDir Path temp) throws Exception {
        File classes = temp.resolve("classes").toFile();
        classes.mkdirs();
        File outer = touchClass(classes, "com/example/Outer.class");
        File inner = touchClass(classes, "com/example/Outer$Inner.class");
        touchClass(classes, "com/example/Stray.class");

        int removed =
                SummerSourceIndex.reconcile(
                        classes, Set.of("com.example.Outer", "com.example.Outer$Inner"));

        assertEquals(1, removed);
        assertTrue(outer.exists());
        assertTrue(inner.exists());
    }

    @Test
    void reconcilePreservesLocalAndAnonymousClasses(@TempDir Path temp) throws Exception {
        File classes = temp.resolve("classes").toFile();
        classes.mkdirs();
        File outer = touchClass(classes, "com/example/Outer.class");
        File localRecord = touchClass(classes, "com/example/Outer$1Req.class");
        File anon = touchClass(classes, "com/example/Outer$1.class");
        File nested = touchClass(classes, "com/example/Outer$Inner.class");
        File nestedLocal = touchClass(classes, "com/example/Outer$Inner$1Local.class");
        File staleOuter = touchClass(classes, "com/example/Deleted$1.class");
        File staleMember = touchClass(classes, "com/example/Outer$DeletedMember.class");

        int removed =
                SummerSourceIndex.reconcile(
                        classes, Set.of("com.example.Outer", "com.example.Outer$Inner"));

        assertEquals(2, removed);
        assertTrue(outer.exists());
        assertTrue(localRecord.exists());
        assertTrue(anon.exists());
        assertTrue(nested.exists());
        assertTrue(nestedLocal.exists());
        assertFalse(staleOuter.exists());
        assertFalse(staleMember.exists());
    }

    @Test
    void reconcileIsIdempotent(@TempDir Path temp) throws Exception {
        File classes = temp.resolve("classes").toFile();
        classes.mkdirs();
        touchClass(classes, "com/example/Alive.class");

        int first = SummerSourceIndex.reconcile(classes, Set.of("com.example.Alive"));
        int second = SummerSourceIndex.reconcile(classes, Set.of("com.example.Alive"));

        assertEquals(0, first);
        assertEquals(0, second);
    }

    @Test
    void reconcileOnMissingDirectoryIsNoOp(@TempDir Path temp) throws Exception {
        File missing = temp.resolve("does-not-exist").toFile();
        int removed = SummerSourceIndex.reconcile(missing, Set.of("com.example.Foo"));
        assertEquals(0, removed);
    }

    // ── snapshot ─────────────────────────────────────────────────────────────────

    @Test
    void writeSnapshotRoundTrips(@TempDir Path temp) throws Exception {
        File basedir = temp.toFile();
        Map<String, List<String>> snapshot =
                Map.of(
                        "src/main/java/com/example/Foo.java",
                        List.of("com.example.Foo", "com.example.Foo$Inner"),
                        "src/main/java/com/example/Bar.java",
                        List.of("com.example.Bar"));

        SummerSourceIndex.writeSnapshot(basedir, snapshot);

        Map<String, List<String>> read = SummerSourceIndex.readSnapshot(basedir);
        assertEquals(snapshot, read);
    }

    @Test
    void readSnapshotMissingFileReturnsEmpty(@TempDir Path temp) throws Exception {
        Map<String, List<String>> result = SummerSourceIndex.readSnapshot(temp.toFile());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void readSnapshotCorruptFileReturnsEmpty(@TempDir Path temp) throws Exception {
        Path stateFile = temp.resolve("target/summer/source-classes.tsv");
        Files.createDirectories(stateFile.getParent());
        Files.writeString(
                stateFile, "totally\nbroken lines with no tabs at all\n", StandardCharsets.UTF_8);

        Map<String, List<String>> result = SummerSourceIndex.readSnapshot(temp.toFile());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void readSnapshotSkipsCommentAndBlankLines(@TempDir Path temp) throws Exception {
        File basedir = temp.toFile();
        Map<String, List<String>> snapshot =
                Map.of("src/main/java/com/example/Foo.java", List.of("com.example.Foo"));
        SummerSourceIndex.writeSnapshot(basedir, snapshot);

        Map<String, List<String>> read = SummerSourceIndex.readSnapshot(basedir);
        assertEquals(1, read.size());
        assertEquals(List.of("com.example.Foo"), read.get("src/main/java/com/example/Foo.java"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static void writeSource(Path root, String relative, String content) throws Exception {
        File file = root.resolve(relative).toFile();
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    private static File touchClass(File root, String relative) throws Exception {
        File file = new File(root, relative);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), new byte[] {0});
        return file;
    }
}
