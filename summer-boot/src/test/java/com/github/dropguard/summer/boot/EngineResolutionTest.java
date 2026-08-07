package com.github.dropguard.summer.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Boot engine-resolution pins (ARCH-AUDIT P2-1): {@code SummerApplication}'s pre-container engine
 * read — YAML value wins, the {@code FrameworkConfig.DEV_ENGINE} default applies when absent, and
 * an invalid value fails fast with a readable error. The private static method is exercised via
 * reflection; each test swaps {@code application.yml} on the test classpath.
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
        java.lang.reflect.InvocationTargetException ite =
                assertThrows(
                        java.lang.reflect.InvocationTargetException.class,
                        EngineResolutionTest::resolveBootstrapEngine);
        org.junit.jupiter.api.Assertions.assertTrue(
                ite.getCause() instanceof ConfigurationException);
        org.junit.jupiter.api.Assertions.assertTrue(ite.getCause().getMessage().contains("fly"));
    }

    @Test
    void systemPropertyOverridesYaml() throws Exception {
        Files.writeString(CLASSES_YML, "summer:\n  engine: runtime\n", StandardCharsets.UTF_8);
        System.setProperty("summer.engine", "aot");
        assertEquals(Engine.AOT, resolveBootstrapEngine());
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
        java.lang.reflect.InvocationTargetException ite =
                assertThrows(
                        java.lang.reflect.InvocationTargetException.class,
                        EngineResolutionTest::resolveBootstrapEngine);
        org.junit.jupiter.api.Assertions.assertTrue(
                ite.getCause() instanceof ConfigurationException);
        org.junit.jupiter.api.Assertions.assertTrue(ite.getCause().getMessage().contains("fly"));
    }

    private static Engine resolveBootstrapEngine() throws Exception {
        Method m = SummerApplication.class.getDeclaredMethod("resolveBootstrapEngine");
        m.setAccessible(true);
        return (Engine) m.invoke(null);
    }
}
