package com.github.dropguard.summer.plugin.dev;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavior of the incremental compiler: valid sources compile into the output dir, broken ones
 * fail.
 */
class HotCompilerTest {

    @TempDir Path tempDir;

    @Test
    void compilesValidSourceIntoOutputDir() throws Exception {
        Path src = tempDir.resolve("src");
        Path out = tempDir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.writeString(
                src.resolve("Greeting.java"),
                "public class Greeting { public String hello() { return \"hi\"; } }\n");

        HotCompiler compiler = new HotCompiler("", out.toFile());
        boolean ok = compiler.compile(List.of(src.resolve("Greeting.java").toFile()));

        assertTrue(ok, "a valid source must compile");
        assertTrue(
                Files.exists(out.resolve("Greeting.class")),
                "the .class must land in the output dir");
    }

    @Test
    void rejectsBrokenSource() throws Exception {
        Path src = tempDir.resolve("src");
        Path out = tempDir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.writeString(src.resolve("Broken.java"), "public class Broken { this is not java }\n");

        HotCompiler compiler = new HotCompiler("", out.toFile());
        boolean ok = compiler.compile(List.of(src.resolve("Broken.java").toFile()));

        assertFalse(ok, "a broken source must fail the compile");
        assertFalse(Files.exists(out.resolve("Broken.class")), "no .class may be produced");
    }

    @Test
    void emptyFileListIsATrivialSuccess() throws Exception {
        HotCompiler compiler = new HotCompiler("", tempDir.toFile());
        assertTrue(compiler.compile(List.of()), "no files to compile is not a failure");
    }
}
