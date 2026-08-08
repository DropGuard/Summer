package com.github.dropguard.summer.tck.di;

import com.github.dropguard.summer.tck.negative.fixtures.di.PackagePrivateBeanConfig;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * A {@code @Bean} whose return type is package-private must fail discovery with a clear error: the
 * AOT wiring references the product class from another package, where a non-public type is not
 * accessible. This is the contract that makes the AOT-first stance fail fast instead of breaking
 * the generated code at compile time.
 */
public class PackagePrivateBeanRejectedTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(
                            PackagePrivateBeanConfig.class, PackagePrivateBeanConfig.productClass())
                    .shouldFail()
                    .build();

    @DualEngine
    void packagePrivateBeanProductRejected() {}
}
