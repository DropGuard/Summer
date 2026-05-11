package summer.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import summer.core.SummerException;

/**
 * A lightweight YAML configuration loader that maps {@code application.yml}
 * directly to immutable Java Records using Jackson's YAML dataformat module.
 *
 * <p>
 * Usage:
 * 
 * <pre>{@code
 * record ServerConfig(int port) {
 * }
 * 
 * ServerConfig config = YamlConfigLoader.load("application.yml", ServerConfig.class);
 * }</pre>
 */
public final class YamlConfigLoader {

	private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private YamlConfigLoader() {
	}

	/**
	 * Loads a YAML classpath resource and deserializes it into a Java Record.
	 *
	 * @param classpathResource
	 *            the resource path (e.g. "application.yml")
	 * @param recordType
	 *            the target Record class
	 * @return a fully populated, immutable Record instance
	 */
	public static <T> T load(String classpathResource, Class<T> recordType) {
		try (InputStream stream = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream(classpathResource)) {
			if (stream == null) {
				throw new SummerException("Configuration file not found on classpath: " + classpathResource);
			}
			return YAML_MAPPER.readValue(stream, recordType);
		} catch (SummerException e) {
			throw e;
		} catch (Exception e) {
			throw new SummerException(
					"Failed to parse YAML configuration '" + classpathResource + "': " + e.getMessage(), e);
		}
	}

	/**
	 * Loads a YAML classpath resource, or returns a default value if the file is
	 * not found.
	 */
	public static <T> T loadOrDefault(String classpathResource, Class<T> recordType, T defaultValue) {
		try (InputStream stream = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream(classpathResource)) {
			if (stream == null) {
				return defaultValue;
			}
			return YAML_MAPPER.readValue(stream, recordType);
		} catch (Exception e) {
			throw new SummerException(
					"Failed to parse YAML configuration '" + classpathResource + "': " + e.getMessage(), e);
		}
	}
}
