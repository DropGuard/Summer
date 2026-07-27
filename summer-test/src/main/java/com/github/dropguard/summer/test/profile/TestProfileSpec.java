package com.github.dropguard.summer.test.profile;

import java.util.Map;

/**
 * Declarative test profile, Quarkus-style (minimal subset).
 *
 * <p>A profile lets a test run the <em>same</em> bean universe under different configuration — the
 * canonical way to exercise conditional/sad-path branches without reshaping bean discovery. It
 * overrides {@code @ConfigurationProperties} binding only; it does not add or remove beans. The
 * bean universe is always the full test universe (whole application plus test beans), so a profile
 * never reshapes discovery — it only changes configuration values, exactly as a Quarkus
 * {@code @TestProfile} does.
 *
 * <pre>{@code
 * public class DevProfile implements TestProfileSpec {
 *     public Map<String, Object> configOverrides() {
 *         return Map.of("server.port", 18080, "feature.toggle", true);
 *     }
 * }
 *
 * &#64;TestProfile(DevProfile.class)
 * class FeatureToggleTest { ... }
 * }</pre>
 *
 * <p>Override keys are dotted paths in the original YAML key form (e.g. {@code server.port}); the
 * framework normalizes them to match record components and applies them through the shared {@code
 * ConfigBinder} chokepoint, so both DI engines (Runtime and AOT) see identical overrides.
 */
public interface TestProfileSpec {

    /**
     * A human-readable profile name, surfaced in test display names.
     *
     * @return profile name (empty string allowed)
     */
    default String name() {
        return "";
    }

    /**
     * Configuration-property overrides applied for this profile. Keys are dotted paths in the
     * original YAML key form; values are the raw bound values.
     *
     * @return override map (empty by default — no changes)
     */
    default Map<String, Object> configOverrides() {
        return Map.of();
    }
}
