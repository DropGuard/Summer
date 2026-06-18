package summer.aot.generator.dummy;
import summer.core.config.ConfigurationProperties;
@ConfigurationProperties("dummy")
public record DummyConfigProperties(String name) {}
