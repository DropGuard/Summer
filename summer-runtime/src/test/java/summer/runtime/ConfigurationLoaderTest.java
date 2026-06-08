package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;

/**
 * Tests for {@link ConfigurationLoader} — file/section absence handling and
 * {@code @DefaultValue} integration.
 */
class ConfigurationLoaderTest {

	private final ConfigurationLoader loader = new ConfigurationLoader();

	@ConfigurationProperties(prefix = "server")
	record AllDefaulted(@DefaultValue("8080") Integer port, @DefaultValue("localhost") String host) {
	}

	@ConfigurationProperties(prefix = "server")
	record PartiallyDefaulted(Integer port, @DefaultValue("localhost") String host) {
	}

	@ConfigurationProperties(prefix = "server")
	record NoDefaults(Integer port, String host) {
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
		void nullWhenFieldLacksDefault() {
			PartiallyDefaulted result = loader.bind("nonexistent.yml", PartiallyDefaulted.class, "server");
			assertNull(result.port(), "Field without @DefaultValue should be null when absent");
			assertEquals("localhost", result.host(), "Field with @DefaultValue should use default");
		}

		@Test
		void nullWhenNoDefaultsAtAll() {
			NoDefaults result = loader.bind("nonexistent.yml", NoDefaults.class, "server");
			assertNull(result.port(), "Field without @DefaultValue should be null");
			assertNull(result.host(), "Field without @DefaultValue should be null");
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
		void nullWhenFieldLacksDefault() {
			PartiallyDefaulted result = loader.bind(YAML, PartiallyDefaulted.class, "server");
			assertNull(result.port(), "Field without @DefaultValue should be null when section absent");
			assertEquals("localhost", result.host(), "Field with @DefaultValue should use default");
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
		void nullWhenMissingFieldHasNoDefault() {
			// YAML only has "server" section with port and host.
			// Bind with prefix "app" → section absent → PartiallyDefaulted has no default
			// for port → port is null.
			PartiallyDefaulted result = loader.bind(YAML, PartiallyDefaulted.class, "app");
			assertNull(result.port(), "Field without @DefaultValue should be null when section absent");
			assertEquals("localhost", result.host(), "Field with @DefaultValue should use default");
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
