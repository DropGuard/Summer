package com.github.dropguard.summer.aot;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.aot.testfixtures.collisiona.EnumCollisionConfig;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.test.NarrowIndexBuilder;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import java.util.Map;
import org.jboss.jandex.IndexView;
import org.junit.jupiter.api.Test;

/**
 * Two DISTINCT enum types with the same simple name in one config interface would collide on the
 * generated enumValue<Mode> helper — the generator must fail at generation time. The common shape
 * (the same enum returned by several methods) stays legal.
 */
class EnumHelperCollisionTest {

    @Test
    void distinctEnumsWithSameSimpleNameFailAtGenerationTime() {
        // The nested Mode classes must be seeded too: the narrow index holds exactly the given
        // classes, and isEnumType resolves the return type against it.
        IndexView index =
                NarrowIndexBuilder.build(
                                com.github.dropguard.summer.aot.testfixtures.collisiona
                                        .EnumCollisionConfig.class,
                                com.github.dropguard.summer.aot.testfixtures.collisiona
                                        .EnumCollisionConfig.Mode.class,
                                com.github.dropguard.summer.aot.testfixtures.collisionb
                                        .EnumCollisionConfig.class,
                                com.github.dropguard.summer.aot.testfixtures.collisionb
                                        .EnumCollisionConfig.Mode.class)
                        .main();
        ConfigImplGenerator generator = new ConfigImplGenerator(index);
        MethodSpec.Builder wire = MethodSpec.methodBuilder("wire");
        assertThrows(
                IllegalStateException.class,
                () ->
                        generator.emitConfigPropertiesInstantiation(
                                wire,
                                new ConfigPropertiesBean(
                                        EnumCollisionConfig.class.getName(), "EnumCollisionConfig"),
                                ClassName.get(EnumCollisionConfig.class),
                                "config",
                                Map.of()));
    }
}
