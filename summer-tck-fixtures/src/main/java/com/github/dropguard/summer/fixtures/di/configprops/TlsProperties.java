package com.github.dropguard.summer.fixtures.di.configprops;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/**
 * Test fixture: Quarkus-style config mapping with non-bean return types (Boolean, String, Integer).
 * These types are NOT beans — they must be bound from YAML, not resolved through the dependency
 * graph.
 *
 * <p>Regression test: the dependency graph must NOT attempt to resolve these params as bean
 * dependencies (which would throw NoSuchBeanException).
 */
@ConfigMapping(prefix = "server.tls")
public interface TlsProperties {

    @WithDefault("false")
    Boolean enabled();

    @WithDefault("")
    String certChain();

    @WithDefault("")
    String privateKey();

    @WithDefault("8443")
    Integer port();
}
