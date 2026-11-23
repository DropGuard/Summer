package com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer;

/** Produced by two {@code @Bean} methods of the same return type, one of them conditional. */
public interface Greeter {
    String greeting();
}
