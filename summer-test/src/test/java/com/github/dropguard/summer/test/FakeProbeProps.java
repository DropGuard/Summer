package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.config.ConfigMapping;

/** Top-level mapping (the @ConfigMapping contract) bound from the TestResource's overrides. */
@ConfigMapping(prefix = "probe")
public interface FakeProbeProps {

    String key();

    String injected();
}
