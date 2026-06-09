package summer.core.config;

import java.util.Map;

/**
 * Resolves {@link DefaultValue} annotations and applies them to a configuration
 * map before Jackson deserialization.
 *
 * <p>
 * Two implementations are provided:
 * </p>
 * <ul>
 * <li>{@code ReflectionDefaultValueResolver} — uses Java reflection at runtime
 * (requires the runtime DI engine)</li>
 * <li>{@code AotDefaultValueResolver} — uses a pre-computed registry populated
 * at build time by the Summer Maven plugin (no reflection required)</li>
 * </ul>
 */
public interface DefaultValueResolver {

	/**
	 * For each record component of {@code type} that has a {@link DefaultValue}
	 * annotation and is absent from {@code section}, inserts the parsed default
	 * value.
	 *
	 * @param section
	 *            the configuration map (mutable); keys are camelCase field names
	 * @param type
	 *            the target record type
	 */
	void applyDefaults(Map<String, Object> section, Class<?> type);
}
