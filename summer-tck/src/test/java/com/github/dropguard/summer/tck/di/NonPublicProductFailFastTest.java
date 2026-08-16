package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.product.NonPublicProductConfig;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;

/**
 * Behavioral fail-fast contract: a {@code @Bean} product whose return type is package-private must
 * fail the AOT build with the clear message, not an obscure javac error about the generated
 * cross-package reference. AOT-only by design: the runtime engine is reflection-based and can
 * legitimately instantiate non-public types, so the two engines intentionally differ here.
 */
public class NonPublicProductFailFastTest {

    @Test
    void nonPublicBeanReturnTypeFailsAotBuild() {
        BeanCreationException e =
                assertThrows(
                        BeanCreationException.class,
                        () ->
                                TestContainer.builder()
                                        .testClass(NonPublicProductFailFastTest.class)
                                        .engine(Engine.AOT)
                                        .beans(NonPublicProductConfig.class)
                                        .build(),
                        "the AOT engine must reject a non-public @Bean return type");

        assertTrue(
                e.getMessage().contains("must be public"),
                "expected the fail-fast message, got: " + e.getMessage());
    }
}
