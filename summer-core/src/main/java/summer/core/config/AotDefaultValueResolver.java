package summer.core.config;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@link DefaultValue} annotations using a pre-computed registry
 * populated at build time by the Summer Maven plugin.
 *
 * <p>
 * This implementation does not use reflection, making it suitable for
 * AOT-compiled environments (e.g. GraalVM native image). The registry stores
 * both the raw default string and the target type for each field, so no
 * reflection is needed at runtime.
 * </p>
 */
public class AotDefaultValueResolver implements DefaultValueResolver {

	private static final Logger log = LoggerFactory.getLogger(AotDefaultValueResolver.class);

	/**
	 * A field entry holding the raw default string and the target type.
	 */
	public record FieldEntry(String defaultValue, Class<?> targetType) {
	}

	/**
	 * Registry: record class → (field name → FieldEntry).
	 */
	private static final Map<Class<?>, Map<String, FieldEntry>> REGISTRY = new ConcurrentHashMap<>();

	/**
	 * Registers default values for a record type. Called by Maven-plugin-generated
	 * code during static initialization.
	 *
	 * @param type
	 *            the record class
	 * @param defaults
	 *            map of field name → raw {@code @DefaultValue} string
	 * @param fieldTypes
	 *            map of field name → target type
	 */
	public static void register(Class<?> type, Map<String, String> defaults, Map<String, Class<?>> fieldTypes) {
		Map<String, FieldEntry> entries = new ConcurrentHashMap<>();
		for (Map.Entry<String, String> e : defaults.entrySet()) {
			String name = e.getKey();
			Class<?> fieldType = fieldTypes.get(name);
			if (fieldType != null) {
				entries.put(name, new FieldEntry(e.getValue(), fieldType));
			}
		}
		REGISTRY.put(type, Collections.unmodifiableMap(entries));
	}

	@Override
	public void applyDefaults(Map<String, Object> section, Class<?> type) {
		Map<String, FieldEntry> defaults = REGISTRY.get(type);
		if (defaults == null) {
			return;
		}

		for (Map.Entry<String, FieldEntry> entry : defaults.entrySet()) {
			String name = entry.getKey();
			if (section.containsKey(name)) {
				continue;
			}

			FieldEntry fe = entry.getValue();
			Object converted = TypeConverter.convert(fe.defaultValue(), fe.targetType());
			section.put(name, converted);
			log.trace("Applied AOT @DefaultValue(\"{}\") to {}.{}", fe.defaultValue(), type.getSimpleName(), name);
		}
	}
}
