package com.github.dropguard.summer.tck.negative.fixtures.di;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

@Configuration
public class PackagePrivateBeanConfig {

    /**
     * Lets a test seed the package-private product into a narrow index without naming the type
     * cross-package (the test lives in another package, where the type is not accessible — which is
     * exactly the rule under test).
     */
    public static Class<?> productClass() {
        return PackagePrivateProduct.class;
    }

    @Bean
    PackagePrivateProduct product() {
        return new PackagePrivateProduct();
    }
}
