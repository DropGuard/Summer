package com.github.dropguard.summer.fixtures.dummy;

import com.github.dropguard.summer.core.config.ConfigMapping;

@ConfigMapping(prefix = "dummy")
public interface DummyConfigProperties {

    String host();

    int port();
}
