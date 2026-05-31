package summer.core.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.exception.ConfigurationException;

/**
 * Unit tests for {@link ConfigurationBinder}.
 */
class ConfigurationBinderTest {

	// Test records
	public record ServerConfig(int port, int connectionTimeout, int maxBodySize, int readTimeout) {
	}

	public record JwtConfig(String secret, long expiration) {
	}

	public record DatabaseConfig(String url, String username, String password) {
	}

	public record AppConfig(ServerConfig server, JwtConfig jwt, DatabaseConfig database) {
	}

	public record SimpleConfig(String value) {
	}

	@Test
	void shouldBindSimpleProperties() {
		ServerConfig config = ConfigurationBinder.bind("test-config.yml", ServerConfig.class);

		assertEquals(9090, config.port());
		assertEquals(30000, config.connectionTimeout());
		assertEquals(10485760, config.maxBodySize());
		assertEquals(10000, config.readTimeout());
	}

	@Test
	void shouldBindNestedProperties() {
		AppConfig config = ConfigurationBinder.bind("test-config.yml", AppConfig.class);

		assertNotNull(config.jwt());
		assertEquals("test-secret-for-unit-tests", config.jwt().secret());
		assertEquals(3600000, config.jwt().expiration());

		assertNotNull(config.database());
		assertEquals("jdbc:h2:mem:test", config.database().url());
		assertEquals("sa", config.database().username());
		assertEquals("", config.database().password());
	}

	@Test
	void shouldExtractPrefix() {
		JwtConfig config = ConfigurationBinder.bind("test-config.yml", JwtConfig.class, "jwt");

		assertEquals("test-secret-for-unit-tests", config.secret());
		assertEquals(3600000, config.expiration());
	}

	@Test
	void shouldExtractNestedPrefix() {
		DatabaseConfig config = ConfigurationBinder.bind("test-config.yml", DatabaseConfig.class, "database");

		assertEquals("jdbc:h2:mem:test", config.url());
		assertEquals("sa", config.username());
		assertEquals("", config.password());
	}

	@Test
	void shouldReturnDefaultWhenFileNotFound() {
		ServerConfig defaultConfig = new ServerConfig(8080, 30000, 10485760, 10000);
		ServerConfig config = ConfigurationBinder.bindOrDefault("nonexistent.yml", ServerConfig.class, defaultConfig);

		assertSame(defaultConfig, config);
	}

	@Test
	void shouldReturnDefaultWithPrefixWhenFileNotFound() {
		JwtConfig defaultConfig = new JwtConfig("default-secret", 3600000);
		JwtConfig config = ConfigurationBinder.bindOrDefault("nonexistent.yml", JwtConfig.class, "jwt", defaultConfig);

		assertSame(defaultConfig, config);
	}

	@Test
	void shouldThrowWhenFileNotFound() {
		assertThrows(ConfigurationException.class, () -> {
			ConfigurationBinder.bind("nonexistent.yml", ServerConfig.class);
		});
	}

	@Test
	void shouldThrowWhenPrefixNotFound() {
		assertThrows(ConfigurationException.class, () -> {
			ConfigurationBinder.bind("test-config.yml", JwtConfig.class, "nonexistent");
		});
	}

	@Test
	void shouldThrowOnMalformedYaml() {
		assertThrows(ConfigurationException.class, () -> {
			ConfigurationBinder.bind("test-malformed.yml", SimpleConfig.class);
		});
	}

	@Test
	void shouldIgnoreUnknownFields() {
		// test-config.yml has more fields than SimpleConfig
		// This should not throw due to FAIL_ON_UNKNOWN_PROPERTIES = false
		assertDoesNotThrow(() -> {
			SimpleConfig config = ConfigurationBinder.bind("test-config.yml", SimpleConfig.class);
			assertNull(config.value()); // value is not in test-config.yml
		});
	}
}
