package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/**
 * Declares the producer whose return type is an interface. The lambda implements nothing but {@link
 * ContractCache} (which has no parent interfaces), so before the product-type fix the runtime
 * engine rejected assembly ("implements no interfaces") and the AOT engine silently registered the
 * raw product — neither engine resolved {@code getBean(ContractCache)} to a proxy.
 */
@Configuration
public class ContractCacheConfig {

    @Bean
    public ContractCache contractCache() {
        return key -> "cached:" + key;
    }
}
