package com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/**
 * The unconditional producer of {@link Greeter}. It must survive on its own: a type-keyed evaluator
 * let the sibling producer's failing condition decide this one's fate too.
 */
@Configuration
public class PlainGreeterConfig {
    @Bean
    public Greeter plainGreeter() {
        return () -> "from-plain";
    }
}
