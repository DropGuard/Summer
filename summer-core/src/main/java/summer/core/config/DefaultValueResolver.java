package summer.core.config;

import java.util.Map;

@FunctionalInterface
public interface DefaultValueResolver {

	void applyDefaults(Map<String, Object> section, Class<?> type);
}
