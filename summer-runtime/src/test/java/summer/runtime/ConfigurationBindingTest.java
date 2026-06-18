package summer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.core.config.DefaultValue;
import summer.runtime.config.AllTypesConfig;
import summer.runtime.config.EmptyDefaultsConfig;
import summer.runtime.config.PartialYamlConfig;

/**
 * TCK for {@code @ConfigurationProperties} binding with {@code @DefaultValue}.
 */
class ConfigurationBindingTest {

	private BeanContainer context;

	@AfterEach
	void tearDown() {
		if (context instanceof AutoCloseable ac) {
			try {
				ac.close();
			} catch (Exception ignored) {
			}
		}
	}

	private BeanContainer createContext(Class<?>... components) {
		var builder = RuntimeApplicationContext.builder();
		for (Class<?> c : components) {
			builder.registerComponent(c);
		}
		context = builder.build();
		return context;
	}

	@Nested
	class TypeConversion {

		@Test
		void bindsAllTypesFromYaml() {
			BeanContainer ctx = createContext(AllTypesConfig.class);
			AllTypesConfig config = ctx.getBean(AllTypesConfig.class);

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
			BeanContainer ctx = createContext(EmptyDefaultsConfig.class);
			EmptyDefaultsConfig config = ctx.getBean(EmptyDefaultsConfig.class);

			assertNotNull(config);
			assertEquals("", config.emptyStr());
			assertEquals(0, config.zeroInt());
			assertEquals(0L, config.zeroLong());
			assertEquals(0.0, config.zeroDouble(), 0.001);
			assertFalse(config.falseBool());
		}
	}

	@Nested
	class PartialYaml {

		@Test
		void mergesYamlWithDefaults() {
			BeanContainer ctx = createContext(PartialYamlConfig.class);
			PartialYamlConfig config = ctx.getBean(PartialYamlConfig.class);

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
