package summer.tck.di;

import java.util.Map;
import summer.test.profile.TestProfileSpec;

/**
 * Test profile used by {@code ProfileBehaviorTest} to verify that
 * {@code @TestProfile} config overrides are applied identically by the Runtime
 * and AOT engines (dual-engine consistency for configuration).
 */
public class DevProfile implements TestProfileSpec {

	@Override
	public String name() {
		return "dev";
	}

	@Override
	public Map<String, Object> configOverrides() {
		return Map.of("app.name", "overridden-by-profile", "app.port", 9999);
	}
}
