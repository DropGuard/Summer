package summer.core.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link ConfigurationBinder}.
 */
class ConfigurationBinderIntegrationTest {

	@BeforeAll
	static void setupResolver() {
		AotDefaultValueResolver.register(JwtProperties.class,
				Map.of("secret", "default-secret", "expiration", "7200000"),
				Map.of("secret", String.class, "expiration", Long.class));
		AotDefaultValueResolver.register(ServerProperties.class,
				Map.of("port", "8080", "connectionTimeout", "30000", "maxBodySize", "10485760", "readTimeout", "10000"),
				Map.of("port", Integer.class, "connectionTimeout", Integer.class, "maxBodySize", Integer.class,
						"readTimeout", Integer.class));
		AotDefaultValueResolver.register(DatabaseProperties.class,
				Map.of("url", "jdbc:h2:mem:default", "username", "root", "password", "password"),
				Map.of("url", String.class, "username", String.class, "password", String.class));
		ConfigurationBinder.setDefaultResolver(new AotDefaultValueResolver());
	}
	public record JwtProperties(@DefaultValue("default-secret") String secret,
			@DefaultValue("7200000") Long expiration) {
	}

	public record ServerProperties(@DefaultValue("8080") Integer port, @DefaultValue("30000") Integer connectionTimeout,
			@DefaultValue("10485760") Integer maxBodySize, @DefaultValue("10000") Integer readTimeout) {
	}

	public record DatabaseProperties(@DefaultValue("jdbc:h2:mem:default") String url,
			@DefaultValue("root") String username, @DefaultValue("password") String password) {
	}

	public record ApplicationProperties(JwtProperties jwt, DatabaseProperties database) {
	}

	@Test
	void shouldBindCompleteConfiguration() {
		ApplicationProperties config = ConfigurationBinder.bind(ApplicationProperties.class, "");

		assertNotNull(config.jwt());
		assertEquals("test-secret-for-unit-tests", config.jwt().secret());
		assertEquals(3600000, config.jwt().expiration());

		assertNotNull(config.database());
		assertEquals("jdbc:h2:mem:test", config.database().url());
		assertEquals("sa", config.database().username());
		assertEquals("", config.database().password());
	}

	@Test
	void shouldBindServerProperties() {
		ServerProperties server = ConfigurationBinder.bind(ServerProperties.class, "server");

		assertNotNull(server);
		assertEquals(9090, server.port());
		assertEquals(30000, server.connectionTimeout());
		assertEquals(10485760, server.maxBodySize());
		assertEquals(10000, server.readTimeout());
	}

	@Test
	void shouldBindWithPrefix() {
		JwtProperties jwt = ConfigurationBinder.bind(JwtProperties.class, "jwt");

		assertEquals("test-secret-for-unit-tests", jwt.secret());
		assertEquals(3600000, jwt.expiration());
	}

	@Test
	void shouldUseDefaultValueWhenSectionMissing() {
		JwtProperties jwt = ConfigurationBinder.bind(JwtProperties.class, "nonexistent");

		assertEquals("default-secret", jwt.secret());
		assertEquals(7200000, jwt.expiration());
	}

	@Test
	void shouldUseDefaultValueWithPrefixWhenSectionMissing() {
		DatabaseProperties db = ConfigurationBinder.bind(DatabaseProperties.class, "nonexistent");

		assertEquals("jdbc:h2:mem:default", db.url());
		assertEquals("root", db.username());
		assertEquals("password", db.password());
	}

	@Test
	void shouldHandleEmptyPrefix() {
		ApplicationProperties config = ConfigurationBinder.bind(ApplicationProperties.class, "");

		assertNotNull(config.jwt());
		assertNotNull(config.database());
	}
}
