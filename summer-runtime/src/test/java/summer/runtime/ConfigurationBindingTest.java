package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import summer.core.config.DefaultValue;
import summer.runtime.config.AllTypesConfig;
import summer.runtime.config.EmptyDefaultsConfig;
import summer.runtime.config.PartialYamlConfig;

/**
 * TCK for {@code @ConfigurationProperties} binding with {@code @DefaultValue}.
 */
class ConfigurationBindingTest {

	private final ConfigurationLoader loader = new ConfigurationLoader();

	// ── Type conversion ──────────────────────────────────────────────

	@Nested
	class TypeConversion {

		@Test
		void bindsAllTypesFromYaml() {
			AllTypesConfig config = loader.bind("test-all-types.yml", AllTypesConfig.class, "all-types");

			assertNotNull(config);
			assertEquals("from-yaml", config.name());
			assertEquals("overridden", config.defaultedString());
			assertEquals(100, config.intVal());
			assertEquals(1234567890123L, config.longVal());
			assertEquals(2.718, config.doubleVal(), 0.001);
			assertFalse(config.boolVal());
		}

		@Test
		void bindsStringDefault() {
			AllTypesConfig config = loader.bind("test-loader-section-absent.yml", AllTypesConfig.class, "all-types");
			assertNotNull(config);
			assertEquals("hello", config.defaultedString());
		}

		@Test
		void bindsIntegerDefault() {
			AllTypesConfig config = loader.bind("test-loader-section-absent.yml", AllTypesConfig.class, "all-types");
			assertNotNull(config);
			assertEquals(42, config.intVal());
		}

		@Test
		void bindsLongDefault() {
			AllTypesConfig config = loader.bind("test-loader-section-absent.yml", AllTypesConfig.class, "all-types");
			assertNotNull(config);
			assertEquals(9999999999L, config.longVal());
		}

		@Test
		void bindsDoubleDefault() {
			AllTypesConfig config = loader.bind("test-loader-section-absent.yml", AllTypesConfig.class, "all-types");
			assertNotNull(config);
			assertEquals(3.14, config.doubleVal(), 0.001);
		}

		@Test
		void bindsBooleanDefault() {
			AllTypesConfig config = loader.bind("test-loader-section-absent.yml", AllTypesConfig.class, "all-types");
			assertNotNull(config);
			assertTrue(config.boolVal());
		}
	}

	// ── Missing YAML section ─────────────────────────────────────────

	@Nested
	class MissingSection {

		@Test
		void allDefaultsAppliedWhenSectionAbsent() {
			AllTypesConfig config = loader.bind("test-loader-section-absent.yml", AllTypesConfig.class, "all-types");

			assertNotNull(config);
			assertEquals("unnamed", config.name());
			assertEquals("hello", config.defaultedString());
			assertEquals(42, config.intVal());
		}

		@Test
		void allDefaultsAppliedWhenFileAbsent() {
			AllTypesConfig config = loader.bind("nonexistent.yml", AllTypesConfig.class, "all-types");

			assertNotNull(config);
			assertEquals("hello", config.defaultedString());
		}

		@Test
		void nestedPrefixAbsent() {
			AllTypesConfig config = loader.bind("test-loader-section-absent.yml", AllTypesConfig.class,
					"deep.nested.all-types");

			assertNotNull(config);
			assertEquals("hello", config.defaultedString());
		}
	}

	// ── Partial YAML ─────────────────────────────────────────────────

	@Nested
	class PartialYaml {

		@Test
		void yamlValuesOverrideDefaults() {
			PartialYamlConfig config = loader.bind("test-partial-config.yml", PartialYamlConfig.class, "partial");

			assertNotNull(config);
			assertEquals("localhost", config.host(), "YAML value should be used");
			assertEquals(8080, config.port(), "@DefaultValue should fill missing field");
			assertFalse(config.ssl(), "@DefaultValue should fill missing field");
		}
	}

	// ── Zero/empty defaults ──────────────────────────────────────────

	@Nested
	class ZeroDefaults {

		@Test
		void emptyStringDefaultForString() {
			EmptyDefaultsConfig config = loader.bind("test-loader-section-absent.yml", EmptyDefaultsConfig.class,
					"empty-defaults");

			assertNotNull(config);
			assertEquals("", config.emptyStr());
		}

		@Test
		void zeroDefaultsForNumericTypes() {
			EmptyDefaultsConfig config = loader.bind("test-loader-section-absent.yml", EmptyDefaultsConfig.class,
					"empty-defaults");

			assertNotNull(config);
			assertEquals(0, config.zeroInt());
			assertEquals(0L, config.zeroLong());
			assertEquals(0.0, config.zeroDouble(), 0.001);
			assertFalse(config.falseBool());
		}
	}

	// ── Annotation visibility ────────────────────────────────────────

	@Nested
	class AnnotationVisibility {

		@Test
		void defaultValueVisibleOnAllRecordComponents() {
			for (RecordComponent rc : AllTypesConfig.class.getRecordComponents()) {
				DefaultValue ann = rc.getAnnotation(DefaultValue.class);
				assertNotNull(ann, "@DefaultValue should be visible on " + rc.getName());
			}
		}

		@Test
		void defaultValueValuesCorrect() {
			assertDefaultValue("defaultedString", "hello");
			assertDefaultValue("intVal", "42");
			assertDefaultValue("longVal", "9999999999");
			assertDefaultValue("doubleVal", "3.14");
			assertDefaultValue("boolVal", "true");
		}

		private void assertDefaultValue(String componentName, String expectedValue) {
			for (RecordComponent rc : AllTypesConfig.class.getRecordComponents()) {
				if (rc.getName().equals(componentName)) {
					DefaultValue ann = rc.getAnnotation(DefaultValue.class);
					assertNotNull(ann, "@DefaultValue not visible on " + componentName);
					assertEquals(expectedValue, ann.value(), "@DefaultValue value mismatch on " + componentName);
					return;
				}
			}
			fail("Record component not found: " + componentName);
		}
	}
}
