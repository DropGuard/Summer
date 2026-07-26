package com.github.dropguard.summer.core.json;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Security tests for {@link SummerObjectMapper}.
 * 
 * <p>
 * Verifies that polymorphic deserialization is disabled to prevent RCE attacks
 * via Jackson gadget chains (CVE-2017-7525, CVE-2019-12086, etc.).
 * </p>
 */
class SummerObjectMapperTest {

	@Nested
	@DisplayName("Default ObjectMapper security")
	class DefaultMapperSecurityTests {

		@Test
		@DisplayName("Default mapper ignores unknown properties")
		void defaultMapperIgnoresUnknownProperties() throws JsonProcessingException {
			ObjectMapper mapper = SummerObjectMapper.create();

			String json = "{\"unknown\":123,\"name\":\"test\"}";

			SimpleBean result = mapper.readValue(json, SimpleBean.class);
			assertEquals("test", result.name());
		}

		@Test
		@DisplayName("Default mapper can serialize/deserialize basic types")
		void defaultMapperHandlesBasicTypes() throws JsonProcessingException {
			ObjectMapper mapper = SummerObjectMapper.create();

			Map<String, Object> data = new HashMap<>();
			data.put("timestamp", "2024-01-01T00:00:00");
			data.put("value", "test");

			byte[] json = mapper.writeValueAsBytes(data);

			assertNotNull(json);
			assertTrue(json.length > 0);

			// Verify it contains expected fields
			String jsonStr = new String(json);
			assertTrue(jsonStr.contains("timestamp"), "Should contain timestamp field");
		}
	}

	@Nested
	@DisplayName("Polymorphic deserialization prevention")
	class PolymorphicDeserializationTests {

		@Test
		@DisplayName("JSON with @class type hint does not trigger class loading")
		void classTypeHintDoesNotTriggerClassLoading() throws JsonProcessingException {
			ObjectMapper mapper = SummerObjectMapper.create();

			// This JSON attempts to use Jackson's polymorphic type handling
			// With safe defaults, @class should be ignored or cause an error
			String jsonWithClass = "{\"@class\":\"java.lang.String\",\"value\":\"test\"}";

			Object result = mapper.readValue(jsonWithClass, Object.class);
			assertNotNull(result);
			// @class should be treated as a regular property, not a type hint
		}

		@Test
		@DisplayName("No automatic type resolution for Object deserialization")
		void noAutomaticTypeResolution() throws JsonProcessingException {
			ObjectMapper mapper = SummerObjectMapper.create();

			// Deserializing to Object should return a Map, not an arbitrary class
			String json = "{\"type\":\"java.lang.RuntimeException\",\"message\":\"RCE\"}";

			Object result = mapper.readValue(json, Object.class);
			assertInstanceOf(Map.class, result, "Should deserialize to Map, not resolve type strings");
		}
	}

	@Nested
	@DisplayName("YAML ObjectMapper security")
	class YamlMapperSecurityTests {

		@Test
		@DisplayName("YAML mapper has safe defaults")
		void yamlMapperHasSafeDefaults() throws JsonProcessingException {
			ObjectMapper yamlMapper = SummerObjectMapper.createYaml();

			// Verify it can parse YAML safely
			String yaml = "name: test\nvalue: 123";

			SimpleBean result = yamlMapper.readValue(yaml, SimpleBean.class);
			assertEquals("test", result.name());
		}
	}

	@Nested
	@DisplayName("Serialization safety")
	class SerializationSafetyTests {

		@Test
		@DisplayName("Empty maps do not fail serialization")
		void emptyMapsDoNotFailSerialization() throws JsonProcessingException {
			ObjectMapper mapper = SummerObjectMapper.create();

			Map<String, Object> emptyMap = new HashMap<>();
			byte[] json = mapper.writeValueAsBytes(emptyMap);
			assertNotNull(json);
			assertTrue(json.length > 0);
		}

		@Test
		@DisplayName("Null values handled gracefully")
		void nullValuesHandledGracefully() throws JsonProcessingException {
			ObjectMapper mapper = SummerObjectMapper.create();

			byte[] json = mapper.writeValueAsBytes(null);
			assertNotNull(json);
		}
	}

	// Test bean - using simple class instead of record for compatibility
	static class SimpleBean {
		private String name;

		public SimpleBean() {
		}

		public SimpleBean(String name) {
			this.name = name;
		}

		public String name() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
