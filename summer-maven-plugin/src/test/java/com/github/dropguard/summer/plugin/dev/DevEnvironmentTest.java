package com.github.dropguard.summer.plugin.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DevEnvironmentTest {

    @Test
    void failedCompilationDoesNotRestartApplication() throws Exception {
        Path output = Files.createTempDirectory("summer-dev-output");
        AtomicInteger starts = new AtomicInteger();
        HotCompiler compiler =
                new HotCompiler("", output.toFile()) {
                    @Override
                    public boolean compile(List<File> sourceFiles) {
                        return false;
                    }
                };
        AppProcessManager appManager =
                new AppProcessManager() {
                    @Override
                    public void start(int port, String mainClass, String classpath) {
                        starts.incrementAndGet();
                    }
                };
        DevEnvironment environment =
                new DevEnvironment(
                        compiler, new JandexFastIndexer(), appManager, "example.App", null);

        CompileFailedException failure =
                assertThrows(
                        CompileFailedException.class,
                        () ->
                                environment.rebuild(
                                        List.of(new File("src/main/java/example/App.java"))));

        assertEquals(
                "Compilation failed; the Summer application was not restarted",
                failure.getMessage());
        assertEquals(0, starts.get(), "the child must not restart after compilation failure");
    }
}
