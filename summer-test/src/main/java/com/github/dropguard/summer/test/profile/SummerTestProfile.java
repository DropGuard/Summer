package com.github.dropguard.summer.test.profile;

import java.util.Map;

/**
 * Declarative test profile, Quarkus-style.
 *
 * <p>A profile lets a test run the <em>same</em> bean universe under different configuration — the
 * canonical way to exercise conditional/sad-path branches without reshaping bean discovery.
 *
 * <pre>{@code
 * public class DevProfile implements TestProfileSpec {
 *     public Map<String, Object> configOverrides() {
 *         return Map.of("server.port", 18080, "feature.toggle", true);
 *     }
 * }
 *
 * &#64;SummerTest
 * &#64;TestProfile(DevProfile.class)
 * class FeatureToggleTest { ... }
 * }</pre>
 */
public interface SummerTestProfile {

    /** Human-readable profile name, surfaced in test display names. */
    default String name() {
        return "";
    }

    /**
     * Configuration-property overrides applied for this profile. Keys are dotted paths in the
     * original YAML key form; values are the raw bound values.
     */
    default Map<String, Object> configOverrides() {
        return Map.of();
    }
}
