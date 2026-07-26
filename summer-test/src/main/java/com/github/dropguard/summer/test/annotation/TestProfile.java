package com.github.dropguard.summer.test.annotation;

import com.github.dropguard.summer.test.profile.TestProfileSpec;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects a {@link TestProfileSpec} for the annotated test class.
 *
 * <p>
 * The profile's {@link TestProfileSpec#configOverrides()} are applied to
 * {@code @ConfigurationProperties} binding while the container for this test is
 * built, then cleared afterwards. The bean universe is unchanged — only
 * configuration differs. Both DI engines (Runtime and AOT) receive the same
 * overrides, so a profile expresses a configuration variant, not an engine
 * variant.
 * </p>
 *
 * <p>
 * Combine with {@code @SummerTest} (and, for dual-engine verification,
 * {@code @DualEngine}) — the profile is engine-agnostic.
 * </p>
 *
 * <pre>
 * {@code
 * &#64;SummerTest
 * &#64;TestProfile(DevProfile.class)
 * class DevConfigTest { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestProfile {

	/**
	 * The profile implementation whose overrides should apply to this test.
	 *
	 * @return profile type
	 */
	Class<? extends TestProfileSpec> value();
}
