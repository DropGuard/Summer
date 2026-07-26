package com.github.dropguard.summer.fixtures.validation;

import com.github.dropguard.summer.core.config.ConfigurationProperties;
import com.github.dropguard.summer.core.config.DefaultValue;

/**
 * Test fixture: @ConfigurationProperties with nullable fields. Used to test the
 * Validation Phase.
 */
@ConfigurationProperties(prefix = "tls")
public record TlsConfig(@DefaultValue("false") Boolean enabled, String certChain, String privateKey) {
}
