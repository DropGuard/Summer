package com.github.dropguard.summer.tck.invisible.fixtures.override;

import com.github.dropguard.summer.core.config.ConfigMapping;

/** Config mapping overridden by a {@code @Bean} producer (the producer wins). */
@ConfigMapping(prefix = "override")
public interface OverrideProps {

    String value();
}
