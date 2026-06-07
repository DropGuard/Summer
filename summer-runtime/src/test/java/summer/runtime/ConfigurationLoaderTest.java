package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;
import summer.core.exception.ConfigurationException;

/**
 * Tests for {@link ConfigurationLoader} — file/section absence handling and
 * {@code @DefaultValue} integration.
 */
class ConfigurationLoaderTest {

	private final ConfigurationLoader loader = new ConfigurationLoader();

	@ConfigurationProperties(prefix = "server")
	record AllDefaulted(@DefaultValue("8080") int port, @DefaultValue("localhost") String host) {
	}

	@ConfigurationProperties(prefix = "server")
	record PartiallyDefaulted(int port, @DefaultValue("localhost") String host) {
	}

	@ConfigurationProperties(prefix = "server")
	record NoDefaults(int port, String host) {
	}

	// --- File absent ---

	@Nested
	class FileAbsent {

		@Test
		void returnsDefaultWhenProvided() {
			AllDefaulted fallback = new AllDefaulted(9999, "fallback");
			AllDefaulted result = loader.bindOrDefault("nonexistent.yml", AllDefaulted.class, fallback);
			assertSame(fallback, result);
		}

		@Test
		void usesDefaultValueWhenAllFieldsHaveAnnotation() {
			AllDefaulted result = loader.bind("nonexistent.yml", AllDefaulted.class, "server");
			assertEquals(8080, result.port());
			assertEquals("localhost", result.host());
		}

		@Test
		void throwsWhenFieldLacksDefault() {
			assertThrows(ConfigurationException.class,
					() -> loader.bind("nonexistent.yml", PartiallyDefaulted.class, "server"));
		}

		@Test
		void throwsWhenNoDefaultsAtAll() {
			assertThrows(ConfigurationException.class,
					() -> loader.bind("nonexistent.yml", NoDefaults.class, "server"));
		}
	}

	// --- File present, section absent ---

	@Nested
	class SectionAbsent {

		private static final String YAML = "test-loader-section-absent.yml";

		@Test
		void returnsDefaultWhenProvided() {
			AllDefaulted fallback = new AllDefaulted(9999, "fallback");
			AllDefaulted result = loader.bindOrDefault(YAML, AllDefaulted.class, "server", fallback);
			assertSame(fallback, result);
		}

		@Test
		void usesDefaultValueWhenAllFieldsHaveAnnotation() {
			AllDefaulted result = loader.bind(YAML, AllDefaulted.class, "server");
			assertEquals(8080, result.port());
			assertEquals("localhost", result.host());
		}

		@Test
		void throwsWhenFieldLacksDefault() {
			assertThrows(ConfigurationException.class, () -> loader.bind(YAML, PartiallyDefaulted.class, "server"));
		}
	}

	// --- File present, section present ---

	@Nested
	class SectionPresent {

		private static final String YAML = "test-loader-section-present.yml";

		@Test
		void bindsAllValues() {
			AllDefaulted result = loader.bind(YAML, AllDefaulted.class, "server");
			assertEquals(3000, result.port());
			assertEquals("example.com", result.host());
		}

		@Test
		void fillsDefaultForMissingField() {
			// YAML has port=3000 and host=example.com, but we use PartiallyDefaulted
			// which has @DefaultValue on host. Both fields present → no default needed.
			PartiallyDefaulted result = loader.bind(YAML, PartiallyDefaulted.class, "server");
			assertEquals(3000, result.port());
			assertEquals("example.com", result.host());
		}

		@Test
		void throwsWhenMissingFieldHasNoDefault() {
			// YAML only has "server" section with port and host.
			// Bind with prefix "app" → section absent → PartiallyDefaulted has no default
			// for port.
			assertThrows(ConfigurationException.class, () -> loader.bind(YAML, PartiallyDefaulted.class, "app"));
		}

		@Test
		void noPrefixBindsEntireFile() {
			// The YAML has server.port=3000 and server.host=example.com.
			// Without prefix, "server" becomes a nested key, not matching "port"/"host".
			// So @DefaultValue kicks in.
			AllDefaulted result = loader.bind(YAML, AllDefaulted.class);
			assertEquals(8080, result.port()); // "port" not at root level → default
			assertEquals("localhost", result.host()); // "host" not at root level → default
		}
	}
}
