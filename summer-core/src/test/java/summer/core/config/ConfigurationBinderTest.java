package summer.core.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConfigurationBinder}.
 */
class ConfigurationBinderTest {

	// Test records
	public record ServerConfig(@DefaultValue("8080") Integer port, @DefaultValue("30000") Integer connectionTimeout,
			@DefaultValue("10485760") Integer maxBodySize, @DefaultValue("10000") Integer readTimeout) {
	}

	public record JwtConfig(@DefaultValue("default-secret") String secret, @DefaultValue("3600000") Long expiration) {
	}

	public record DatabaseConfig(String url, String username, String password) {
	}

	public record AppConfig(ServerConfig server, JwtConfig jwt, DatabaseConfig database) {
	}

	public record SimpleConfig(String value) {
	}

	@Test
	void shouldBindSimpleProperties() {
		ServerConfig config = ConfigurationBinder.bind(ServerConfig.class, "server");

		assertEquals(9090, config.port());
		assertEquals(30000, config.connectionTimeout());
		assertEquals(10485760, config.maxBodySize());
		assertEquals(10000, config.readTimeout());
	}

	@Test
	void shouldBindNestedProperties() {
		AppConfig config = ConfigurationBinder.bind(AppConfig.class, "");

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
		JwtConfig config = ConfigurationBinder.bind(JwtConfig.class, "jwt");

		assertEquals("test-secret-for-unit-tests", config.secret());
		assertEquals(3600000, config.expiration());
	}

	@Test
	void shouldExtractNestedPrefix() {
		DatabaseConfig config = ConfigurationBinder.bind(DatabaseConfig.class, "database");

		assertEquals("jdbc:h2:mem:test", config.url());
		assertEquals("sa", config.username());
		assertEquals("", config.password());
	}

	@Test
	void shouldUseDefaultValueWhenSectionMissing() {
		JwtConfig config = ConfigurationBinder.bind(JwtConfig.class, "nonexistent");

		assertEquals("default-secret", config.secret());
		assertEquals(3600000, config.expiration());
	}

	@Test
	void shouldReturnNullsWhenNoDefaultsAndMissingSection() {
		SimpleConfig config = ConfigurationBinder.bind(SimpleConfig.class, "nonexistent");

		assertNull(config.value());
	}

	@Test
	void shouldHandleEmptyPrefix() {
		AppConfig config = ConfigurationBinder.bind(AppConfig.class, "");

		assertNotNull(config.jwt());
		assertNotNull(config.database());
	}

	@Test
	void shouldHandleNullPrefix() {
		AppConfig config = ConfigurationBinder.bind(AppConfig.class, (String) null);

		assertNotNull(config.jwt());
		assertNotNull(config.database());
	}

	@Test
	void shouldIgnoreUnknownFields() {
		assertDoesNotThrow(() -> {
			SimpleConfig config = ConfigurationBinder.bind(SimpleConfig.class, "server");
			assertNull(config.value());
		});
	}
}
