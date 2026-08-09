package com.github.dropguard.summer.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * The compiled-class cache's footprint is bounded: closing the container closes the URLClassLoader
 * that bridges the AOT scratch directory, so the temp dir's deleteOnExit can actually run (the JVM
 * pins a loaded class's defining loader until the process exits otherwise). Close() only frees the
 * handles — the loader can no longer serve resources afterwards.
 */
class CompiledLoaderCloseTest {

    @Test
    void containerCloseClosesTheCompiledClassLoader() throws Exception {
        File dir = Files.createTempDirectory("summer-loader-close-").toFile();
        java.net.URLClassLoader loader =
                new java.net.URLClassLoader(new java.net.URL[] {dir.toURI().toURL()});
        // A class file in the scratch dir, loadable through the loader.
        File cls = new File(dir, "probe/X.class");
        cls.getParentFile().mkdirs();
        Files.write(cls.toPath(), new byte[] {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe});

        BeanContainer.Builder builder = new BeanContainer.Builder();
        builder.register(String.class, "probe");
        BeanContainer container = builder.build(Engine.RUNTIME);
        container.addShutdownTask(
                () -> {
                    try {
                        loader.close();
                    } catch (Exception ignored) {
                    }
                });
        assertNotNull(
                loader.getResource("probe/X.class"), "the scratch dir is reachable before close");
        container.close();
        assertNull(
                loader.getResource("probe/X.class"),
                "after close the loader no longer serves the scratch dir");
    }
}
