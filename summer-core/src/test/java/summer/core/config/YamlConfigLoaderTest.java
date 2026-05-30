package summer.core.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Tests for {@link YamlConfigLoader}. */
public class YamlConfigLoaderTest {

	// Test records that mirror a realistic YAML structure
	public record ServerSection(int port) {
	}

	public record DatabaseSection(String url, String username) {
	}

	public record AppConfig(ServerSection server, DatabaseSection database) {
	}

	@Test
	void shouldLoadSimpleYaml() {
		ServerSection config = YamlConfigLoader.loadOrDefault("test-server.yml", ServerSection.class,
				new ServerSection(8080));
		assertEquals(9090, config.port());
	}

	@Test
	void shouldLoadNestedYaml() {
		AppConfig config = YamlConfigLoader.loadOrDefault("test-app.yml", AppConfig.class, null);
		assertNotNull(config);
		assertEquals(3000, config.server().port());
		assertEquals("jdbc:h2:mem:test", config.database().url());
		assertEquals("sa", config.database().username());
	}

	@Test
	void shouldReturnDefaultWhenFileNotFound() {
		ServerSection fallback = new ServerSection(8080);
		ServerSection result = YamlConfigLoader.loadOrDefault("nonexistent.yml", ServerSection.class, fallback);
		assertSame(fallback, result);
	}

	@Test
	void shouldThrowOnMalformedYaml() {
		assertThrows(Exception.class,
				() -> YamlConfigLoader.loadOrDefault("test-malformed.yml", ServerSection.class, null));
	}
}
