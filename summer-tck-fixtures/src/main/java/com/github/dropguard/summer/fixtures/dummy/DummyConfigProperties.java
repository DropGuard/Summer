package com.github.dropguard.summer.fixtures.dummy;

import com.github.dropguard.summer.core.config.ConfigurationProperties;

@ConfigurationProperties(prefix = "dummy")
public record DummyConfigProperties(String host, int port) {}
