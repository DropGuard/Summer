package com.github.dropguard.summer.tck.invisible.fixtures.closeorder;

/** A {@code @Bean}-produced AutoCloseable — the product of {@link CloseOrderProducerConfig}. */
public class CloseOrderProduct implements AutoCloseable {

    @Override
    public void close() {
        CloseOrderRecorder.record("product");
    }
}
