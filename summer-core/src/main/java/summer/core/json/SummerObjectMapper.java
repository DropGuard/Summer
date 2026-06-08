package summer.core.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.function.Consumer;

/**
 * Factory for creating pre-configured {@link ObjectMapper} instances with safe
 * defaults.
 *
 * <p>
 * <strong>Security Design:</strong> Polymorphic deserialization is disabled to
 * prevent Remote Code Execution (RCE) attacks via Jackson gadget chains. Users
 * must use explicit type conversion with {@link ObjectMapper#convertValue}.
 * </p>
 *
 * <h3>Usage Examples</h3>
 *
 * <pre>
 * // 1. Default (safe)
 * ObjectMapper mapper = SummerObjectMapper.create();
 *
 * // 2. With custom configuration
 * ObjectMapper mapper = SummerObjectMapper.create(m -> {
 * 	m.setSerializationInclusion(JsonInclude.Include.NON_NULL);
 * });
 *
 * // 3. Explicit type conversion (recommended pattern)
 * Object decoded = codec.decodeValue(bytes);
 * MyType result = mapper.convertValue(decoded, MyType.class);
 * </pre>
 */
public final class SummerObjectMapper {

	private SummerObjectMapper() {
		// Utility class
	}

	/**
	 * Creates a default ObjectMapper with safe settings:
	 * <ul>
	 * <li>JavaTimeModule registered</li>
	 * <li>Unknown properties ignored</li>
	 * <li>Polymorphic deserialization disabled</li>
	 * </ul>
	 *
	 * @return a new ObjectMapper with safe defaults
	 */
	public static ObjectMapper create() {
		return create(null);
	}

	/**
	 * Creates a default ObjectMapper with safe settings and optional customization.
	 *
	 * @param customizer
	 *            optional customizer to apply additional configuration
	 * @return a new ObjectMapper with safe defaults
	 */
	public static ObjectMapper create(Consumer<ObjectMapper> customizer) {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		if (customizer != null) {
			customizer.accept(mapper);
		}

		return mapper;
	}

	/**
	 * Creates a YAML-aware ObjectMapper with safe settings.
	 *
	 * @return a new ObjectMapper configured for YAML parsing
	 */
	public static ObjectMapper createYaml() {
		return createYaml(null);
	}

	/**
	 * Creates a YAML-aware ObjectMapper with safe settings and optional
	 * customization.
	 *
	 * @param customizer
	 *            optional customizer to apply additional configuration
	 * @return a new ObjectMapper configured for YAML parsing
	 */
	public static ObjectMapper createYaml(Consumer<ObjectMapper> customizer) {
		ObjectMapper mapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
		mapper.registerModule(new JavaTimeModule());
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		if (customizer != null) {
			customizer.accept(mapper);
		}

		return mapper;
	}
}
