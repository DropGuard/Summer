package com.github.dropguard.summer.tck.invisible.fixtures.narrow.product;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/** {@code @Bean} producer whose return type is package-private — must fail the AOT build fast. */
@Configuration
public class NonPublicProductConfig {

    @Bean
    public NonPublicProduct make() {
        return new NonPublicProduct();
    }
}
