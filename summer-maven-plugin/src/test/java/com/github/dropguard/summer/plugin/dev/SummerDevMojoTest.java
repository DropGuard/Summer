package com.github.dropguard.summer.plugin.dev;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of the dev mojo's main-class auto-detection (Jandex scan for a public static main). */
class SummerDevMojoTest {

    @TempDir Path tempDir;

    @Test
    void detectsClassWithPublicStaticMain() throws Exception {
        index(
                tempDir,
                "AppWithMain",
                "public class AppWithMain { public static void main(String[] args) {} }");

        assertEquals("AppWithMain", findMainClass());
    }

    @Test
    void returnsNullWhenNoMainExists() throws Exception {
        index(
                tempDir,
                "NoMain",
                "public class NoMain { public String helper() { return \"x\"; } }");

        assertNull(findMainClass());
    }

    @Test
    void rejectsMainWithNonJvmSignature() throws Exception {
        // public static int main(String[]) is not the JVM entry contract (must be void).
        index(
                tempDir,
                "FakeMain",
                "public class FakeMain { public static int main(String[] args) { return 0; } }");

        assertNull(findMainClass());
    }

    @Test
    void returnsNullWhenNoIndexExists() throws Exception {
        assertNull(findMainClass());
    }

    /** Compiles the source and writes its jandex.idx under META-INF/. */
    private void index(Path dir, String className, String source) throws Exception {
        Path src = dir.resolve(className + ".java");
        Files.writeString(src, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "the test needs a JDK");
        assertEquals(
                0,
                compiler.run(
                        null,
                        null,
                        null,
                        "-d",
                        dir.toAbsolutePath().toString(),
                        src.toAbsolutePath().toString()));

        File classFile = dir.resolve(className + ".class").toFile();
        Index index = Index.of(classFile);
        Path metaInf = Files.createDirectories(dir.resolve("META-INF"));
        try (FileOutputStream out = new FileOutputStream(metaInf.resolve("jandex.idx").toFile())) {
            new IndexWriter(out).write(index);
        }
    }

    private String findMainClass() throws Exception {
        Method m = SummerDevMojo.class.getDeclaredMethod("findMainClass", File.class);
        m.setAccessible(true);
        return (String) m.invoke(new SummerDevMojo(), tempDir.toFile());
    }
}
