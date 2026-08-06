package com.github.dropguard.summer.aot;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import java.util.Map;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.junit.jupiter.api.Test;

/**
 * Regression: two {@code @ConfigMapping} interfaces with the same simple name in different packages
 * ({@code collisiona.SameNameConfig} and {@code collisionb.SameNameConfig}) both map to {@code
 * generated.SameNameConfig$$ConfigImpl} — the second {@code JavaFile.writeTo} would silently
 * overwrite the first, leaving the wire registering an instance of the wrong interface. Codegen
 * must fail fast instead.
 */
class ConfigImplGeneratorTest {

    @Test
    void sameSimpleNameAcrossPackagesFailsFast() throws Exception {
        IndexView index =
                Index.of(
                        com.github.dropguard.summer.aot.testfixtures.collisiona.SameNameConfig
                                .class,
                        com.github.dropguard.summer.aot.testfixtures.collisionb.SameNameConfig
                                .class);
        ConfigImplGenerator generator = new ConfigImplGenerator(index);
        MethodSpec.Builder wire = MethodSpec.methodBuilder("wire");

        generator.emitConfigPropertiesInstantiation(
                wire,
                new ConfigPropertiesBean(
                        com.github.dropguard.summer.aot.testfixtures.collisiona.SameNameConfig.class
                                .getName(),
                        "SameNameConfig"),
                ClassName.get(
                        com.github.dropguard.summer.aot.testfixtures.collisiona.SameNameConfig
                                .class),
                "configA",
                Map.of());

        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                generator.emitConfigPropertiesInstantiation(
                                        wire,
                                        new ConfigPropertiesBean(
                                                com.github.dropguard.summer.aot.testfixtures
                                                        .collisionb.SameNameConfig.class
                                                        .getName(),
                                                "SameNameConfig"),
                                        ClassName.get(
                                                com.github.dropguard.summer.aot.testfixtures
                                                        .collisionb.SameNameConfig.class),
                                        "configB",
                                        Map.of()),
                        "the second same-simple-name interface must fail fast, not overwrite");

        assertTrue(
                e.getMessage().contains("collision"),
                "the error must name the collision, got: " + e.getMessage());
    }
}
