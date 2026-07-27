package com.github.dropguard.summer.fixtures.validation;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/** Test fixture: {@code @ConfigMapping} with nullable fields. Used to test the Validation Phase. */
@ConfigMapping(prefix = "tls")
public interface TlsConfig {

    @WithDefault("false")
    Boolean enabled();

    String certChain();

    String privateKey();
}
