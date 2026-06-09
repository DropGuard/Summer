package summer.fixtures.validation;

import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;

/**
 * Test fixture: @ConfigurationProperties with nullable fields. Used to test the
 * Validation Phase.
 */
@ConfigurationProperties(prefix = "tls")
public record TlsConfig(@DefaultValue("false") Boolean enabled, String certChain, String privateKey) {
}
