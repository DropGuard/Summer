package summer.fixtures.dummy;

import summer.core.config.ConfigurationProperties;

@ConfigurationProperties(prefix = "dummy")
public record DummyConfigProperties(String host, int port) {
}
