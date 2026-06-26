package summer.test;

import java.util.Map;
import java.util.Set;

/**
 * Optional test profile for customizing the container before it is built.
 *
 * <p>
 * Apply via {@code @TestProfile(MyProfile.class)}. Not used by
 * {@code @SummerTest} directly — only when additional configuration is needed
 * (bean filtering, config overrides).
 * </p>
 */
public interface SummerTestProfile {

	default Set<Class<?>> getEnabledBeans() {
		return Set.of();
	}

	default Map<String, String> getConfigOverrides() {
		return Map.of();
	}
}