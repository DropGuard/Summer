package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import summer.core.config.ConfigurationBinder;
import summer.core.config.DefaultValue;
import summer.runtime.config.AllTypesConfig;
import summer.runtime.config.EmptyDefaultsConfig;
import summer.runtime.config.PartialYamlConfig;

/**
 * TCK for {@code @ConfigurationProperties} binding with {@code @DefaultValue}.
 */
class ConfigurationBindingTest {

	@Nested
	class TypeConversion {

		@Test
		void bindsAllTypesFromYaml() {
			AllTypesConfig config = ConfigurationBinder.bind(AllTypesConfig.class, "all-types");

			assertNotNull(config);
			assertEquals("from-yaml", config.name());
			assertEquals("overridden", config.defaultedString());
			assertEquals(100, config.intVal());
			assertEquals(1234567890123L, config.longVal());
			assertEquals(2.718, config.doubleVal(), 0.001);
			assertFalse(config.boolVal());
		}
	}

	@Nested
	class MissingSection {

		@Test
		void fallsBackToDefaultsWhenSectionMissing() {
			EmptyDefaultsConfig config = ConfigurationBinder.bind(EmptyDefaultsConfig.class, "missing-section");

			assertNotNull(config);
			assertEquals("", config.emptyStr());
			assertEquals(0, config.zeroInt());
			assertEquals(0L, config.zeroLong());
			assertEquals(0.0, config.zeroDouble(), 0.001);
			assertFalse(config.falseBool());
		}

		@Test
		void fallsBackToDefaultsWhenPrefixAbsent() {
			EmptyDefaultsConfig config = ConfigurationBinder.bind(EmptyDefaultsConfig.class, "nonexistent");

			assertNotNull(config);
			assertEquals("", config.emptyStr());
		}

		@Test
		void handlesEmptyPrefix() {
			EmptyDefaultsConfig config = ConfigurationBinder.bind(EmptyDefaultsConfig.class, "");
			assertNotNull(config);
		}

		@Test
		void handlesNullPrefix() {
			EmptyDefaultsConfig config = ConfigurationBinder.bind(EmptyDefaultsConfig.class, (String) null);
			assertNotNull(config);
		}
	}

	@Nested
	class PartialYaml {

		@Test
		void mergesYamlWithDefaults() {
			PartialYamlConfig config = ConfigurationBinder.bind(PartialYamlConfig.class, "partial");

			assertNotNull(config);
			assertEquals("localhost", config.host());
			assertEquals(8080, config.port());
		}
	}

	@Nested
	class AnnotationVisibility {

		@Test
		void defaultValueAnnotationIsRetainedAtRuntime() throws Exception {
			RecordComponent comp = EmptyDefaultsConfig.class.getRecordComponents()[0];
			assertNotNull(comp.getAnnotation(DefaultValue.class), "@DefaultValue must have RUNTIME retention");
		}

		@Test
		void allRecordComponentsHaveDefaultValue() {
			for (RecordComponent comp : EmptyDefaultsConfig.class.getRecordComponents()) {
				assertNotNull(comp.getAnnotation(DefaultValue.class),
						"Every field in EmptyDefaultsConfig must have @DefaultValue");
			}
		}
	}
}
