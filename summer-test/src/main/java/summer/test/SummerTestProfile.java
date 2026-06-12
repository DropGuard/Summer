package summer.test;

import java.util.Map;
import java.util.Set;

public interface SummerTestProfile {

	default Set<Class<?>> getEnabledBeans() {
		return Set.of();
	}

	default Map<String, String> getConfigOverrides() {
		return Map.of();
	}

	default Map<Class<?>, Class<?>> getBeanReplacements() {
		return Map.of();
	}

	default boolean disableGlobalMiddleware() {
		return false;
	}

	default Set<String> tags() {
		return Set.of();
	}
}
