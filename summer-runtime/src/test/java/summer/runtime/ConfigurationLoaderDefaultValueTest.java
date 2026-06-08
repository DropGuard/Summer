package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests that ConfigurationLoader respects @DefaultValue on record components
 * when the YAML section doesn't contain those fields.
 */
class ConfigurationLoaderDefaultValueTest {

	@Test
	void bindsRecordWithDefaultValuesWhenYamlMissing() {
		ConfigurationLoader loader = new ConfigurationLoader();
		// application.yml doesn't have a "test" section — simulates GrpcTlsConfig
		// scenario
		DefaultValueTestRecord result = loader.bind("application.yml", DefaultValueTestRecord.class, "test.nested");

		assertNotNull(result, "Should bind even when YAML section is missing");
		assertFalse(result.enabled(), "@DefaultValue(\"false\") should be applied");
		assertEquals("", result.name(), "@DefaultValue(\"\") should be applied");
	}
}
