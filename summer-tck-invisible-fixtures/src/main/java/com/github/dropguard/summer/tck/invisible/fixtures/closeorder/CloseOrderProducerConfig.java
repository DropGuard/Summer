package com.github.dropguard.summer.tck.invisible.fixtures.closeorder;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/**
 * The producer: its {@code @Bean} product must close before it. AutoCloseable so the dual-engine
 * test can observe the product-then-producer teardown order on both engines.
 */
@Configuration
public class CloseOrderProducerConfig implements AutoCloseable {

    @Bean
    public CloseOrderProduct product() {
        return new CloseOrderProduct();
    }

    @Override
    public void close() {
        CloseOrderRecorder.record("producer");
    }
}
