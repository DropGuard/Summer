package com.github.dropguard.summer.tck.invisible.fixtures.override;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/** A {@code @Bean} producer for a {@code @ConfigMapping} type — the explicit override. */
@Configuration
public class OverrideConfig {

    @Bean
    public OverrideProps overrideProps() {
        return new OverrideProps() {
            @Override
            public String value() {
                return "from-producer";
            }
        };
    }
}
