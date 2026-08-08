package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.config.ConfigMapping;

/** Top-level mapping bound from the ordered resources' merged overrides. */
@ConfigMapping(prefix = "order")
public interface OrderedProps {

    String key();

    String onlyHigh();
}
