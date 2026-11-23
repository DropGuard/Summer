package com.github.dropguard.summer.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Checks bootstrap engine selection before the container is built. Each test controls its
 * equivalent environment value and swaps {@code application.yml} on the test classpath.
 */
class EngineResolutionTest {

    private static final Path CLASSES_YML = Path.of("target/test-classes/application.yml");

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(CLASSES_YML);
        System.clearProperty("summer.engine");
    }

    @Test
    void defaultsToRuntimeWithoutYaml() throws Exception {
        Files.deleteIfExists(CLASSES_YML);
        assertEquals(Engine.RUNTIME, resolveBootstrapEngine());
    }

    @Test
    void yamlValueWins() throws Exception {
        Files.writeString(CLASSES_YML, "summer:\n  engine: aot\n", StandardCharsets.UTF_8);
        assertEquals(Engine.AOT, resolveBootstrapEngine());
    }

    @Test
    void invalidValueFailsFast() throws Exception {
        Files.writeString(CLASSES_YML, "summer:\n  engine: fly\n", StandardCharsets.UTF_8);
        ConfigurationException exception =
                assertThrows(
                        ConfigurationException.class, EngineResolutionTest::resolveBootstrapEngine);
        assertTrue(exception.getMessage().contains("fly"));
    }

    @Test
    void systemPropertyTakesPrecedenceOverOtherSources() throws Exception {
        Files.writeString(CLASSES_YML, "summer:\n  engine: runtime\n", StandardCharsets.UTF_8);
        System.setProperty("summer.engine", "aot");
        assertEquals(Engine.AOT, resolveBootstrapEngine());
        assertEquals(Engine.AOT, SummerApplication.resolveBootstrapEngine("fly"));
    }

    @Test
    void blankSystemPropertyFallsBackToYaml() throws Exception {
        Files.writeString(CLASSES_YML, "summer:\n  engine: aot\n", StandardCharsets.UTF_8);
        System.setProperty("summer.engine", "  ");
        assertEquals(Engine.AOT, resolveBootstrapEngine());
    }

    @Test
    void invalidSystemPropertyFailsFast() throws Exception {
        System.setProperty("summer.engine", "fly");
        ConfigurationException exception =
                assertThrows(
                        ConfigurationException.class, EngineResolutionTest::resolveBootstrapEngine);
        assertTrue(exception.getMessage().contains("fly"));
    }

    private static Engine resolveBootstrapEngine() {
        return SummerApplication.resolveBootstrapEngine(null);
    }
}
