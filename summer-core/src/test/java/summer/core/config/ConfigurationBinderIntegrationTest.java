package summer.core.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link ConfigurationBinder}.
 * 
 * <p>
 * These tests verify the complete configuration binding flow, including YAML
 * parsing, prefix extraction, and type binding.
 * </p>
 */
class ConfigurationBinderIntegrationTest {

	// Test records that mimic real-world configuration structure
	public record JwtProperties(String secret, long expiration) {
	}

	public record ServerProperties(int port, int connectionTimeout, int maxBodySize, int readTimeout) {
	}

	public record DatabaseProperties(String url, String username, String password) {
	}

	public record ApplicationProperties(JwtProperties jwt, DatabaseProperties database) {
	}

	@Test
	void shouldBindCompleteConfiguration() {
		// Bind the entire configuration
		ApplicationProperties config = ConfigurationBinder.bind("test-config.yml", ApplicationProperties.class);

		// Verify JWT properties
		assertNotNull(config.jwt());
		assertEquals("test-secret-for-unit-tests", config.jwt().secret());
		assertEquals(3600000, config.jwt().expiration());

		// Verify database properties
		assertNotNull(config.database());
		assertEquals("jdbc:h2:mem:test", config.database().url());
		assertEquals("sa", config.database().username());
		assertEquals("", config.database().password());
	}

	@Test
	void shouldBindServerProperties() {
		// Bind server properties directly (they are at the top level)
		ServerProperties server = ConfigurationBinder.bind("test-config.yml", ServerProperties.class);

		assertNotNull(server);
		assertEquals(9090, server.port());
		assertEquals(30000, server.connectionTimeout());
		assertEquals(10485760, server.maxBodySize());
		assertEquals(10000, server.readTimeout());
	}

	@Test
	void shouldBindWithPrefix() {
		// Bind only the JWT section
		JwtProperties jwt = ConfigurationBinder.bind("test-config.yml", JwtProperties.class, "jwt");

		assertEquals("test-secret-for-unit-tests", jwt.secret());
		assertEquals(3600000, jwt.expiration());
	}

	@Test
	void shouldBindWithDefaultValue() {
		// Create a default configuration
		JwtProperties defaultJwt = new JwtProperties("default-secret", 7200000);

		// Try to bind from a non-existent file
		JwtProperties jwt = ConfigurationBinder.bindOrDefault("nonexistent.yml", JwtProperties.class, defaultJwt);

		// Should return the default
		assertSame(defaultJwt, jwt);
		assertEquals("default-secret", jwt.secret());
		assertEquals(7200000, jwt.expiration());
	}

	@Test
	void shouldBindWithPrefixAndDefaultValue() {
		// Create a default configuration
		DatabaseProperties defaultDb = new DatabaseProperties("jdbc:h2:mem:default", "root", "password");

		// Try to bind from a non-existent file with prefix
		DatabaseProperties db = ConfigurationBinder.bindOrDefault("nonexistent.yml", DatabaseProperties.class,
				"database", defaultDb);

		// Should return the default
		assertSame(defaultDb, db);
		assertEquals("jdbc:h2:mem:default", db.url());
		assertEquals("root", db.username());
		assertEquals("password", db.password());
	}

	@Test
	void shouldBindWithYamlEnvironmentVariableSyntax() {
		// The test-config.yml uses ${JWT_SECRET:default} syntax
		// Jackson should handle this as a plain string
		JwtProperties jwt = ConfigurationBinder.bind("test-config.yml", JwtProperties.class, "jwt");

		// The secret should be the literal string from YAML
		assertEquals("test-secret-for-unit-tests", jwt.secret());
	}

	@Test
	void shouldHandleEmptyPrefix() {
		// Empty prefix should bind the entire configuration
		ApplicationProperties config = ConfigurationBinder.bind("test-config.yml", ApplicationProperties.class, "");

		assertNotNull(config.jwt());
		assertNotNull(config.database());
	}

	@Test
	void shouldHandleNullPrefix() {
		// Null prefix should bind the entire configuration
		ApplicationProperties config = ConfigurationBinder.bind("test-config.yml", ApplicationProperties.class, null);

		assertNotNull(config.jwt());
		assertNotNull(config.database());
	}
}
