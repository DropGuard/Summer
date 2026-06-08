package summer.tck.di.configprops;

import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;

/**
 * Test fixture: @ConfigurationProperties record with non-bean constructor
 * params (Boolean, String, Integer). These types are NOT beans — they must be
 * bound from YAML, not resolved through the dependency graph.
 *
 * <p>
 * Regression test: the dependency graph must NOT attempt to resolve these
 * params as bean dependencies (which would throw NoSuchBeanException).
 * </p>
 */
@ConfigurationProperties(prefix = "server.tls")
public record TlsProperties(@DefaultValue("false") Boolean enabled, @DefaultValue("") String certChain,
		@DefaultValue("") String privateKey, @DefaultValue("8443") Integer port) {
}
