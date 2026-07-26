package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.configprops.AppProperties;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestProfile;

/**
 * Verifies that {@code @TestProfile} config overrides are applied identically
 * by the Runtime and AOT engines.
 *
 * <p>
 * Run under {@code @DualEngine}, every assertion executes once per engine. If
 * the AOT path ignored profile overrides (a latent risk, since AOT binds config
 * at generation time), the AOT invocation would diverge from Runtime and fail —
 * so this test is also a guard for dual-engine config consistency.
 */
@TestProfile(DevProfile.class)

@SummerTest
public class ProfileBehaviorTest {

	private final BeanContainer context;

	public ProfileBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@DualEngine
	void profileOverridesAppName() {
		AppProperties props = context.getBean(AppProperties.class);
		assertNotNull(props);
		assertEquals("overridden-by-profile", props.name());
	}

	@DualEngine
	void profileOverridesAppPort() {
		AppProperties props = context.getBean(AppProperties.class);
		assertEquals(Integer.valueOf(9999), props.port());
	}
}
