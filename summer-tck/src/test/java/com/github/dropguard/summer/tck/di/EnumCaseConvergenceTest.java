package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.configprops.EnvironmentConfig;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * The enum-conversion contract on both engines: mixed-case enum constants bind from lowercase YAML
 * and from {@code @WithDefault}. The runtime proxy gets this from Jackson's {@code
 * ACCEPT_CASE_INSENSITIVE_ENUMS}; the AOT generated code must mirror it (the old {@code
 * Enum.valueOf(raw.toUpperCase())} threw on mixed-case constants like {@code production}).
 */
@SummerTest
public class EnumCaseConvergenceTest {

    @DualEngine
    void mixedCaseEnumBindsFromLowercaseYaml(BeanContainer container) {
        EnvironmentConfig config = container.getBean(EnvironmentConfig.class);
        assertEquals(
                EnvironmentConfig.Mode.production,
                config.mode(),
                "lowercase YAML must bind the mixed-case constant on both engines");
    }

    @DualEngine
    void mixedCaseEnumWithDefault(BeanContainer container) {
        EnvironmentConfig config = container.getBean(EnvironmentConfig.class);
        assertEquals(
                EnvironmentConfig.Mode.staging,
                config.fallbackMode(),
                "@WithDefault's lowercase value must resolve to the mixed-case constant");
    }
}
