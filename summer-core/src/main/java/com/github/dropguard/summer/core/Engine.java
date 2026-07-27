package com.github.dropguard.summer.core;

import com.github.dropguard.summer.core.exception.ConfigurationException;
import java.util.Locale;

/**
 * DI engine selection enum.
 *
 * <p>Passed to {@link com.github.dropguard.summer.core.DiEngine#create(Engine, Object...)} for
 * explicit engine selection, or resolved from configuration / {@code -Dsummer.engine} via {@link
 * #fromString(String)}. The enum is the single source of truth for valid engine names — callers
 * must go through {@link #fromString(String)} rather than comparing raw strings, so the allowed
 * values live in exactly one place.
 */
public enum Engine {

    /** Compile-time generated context (requires summer-maven-plugin). */
    AOT,

    /** Runtime DI engine: reads Jandex index at startup. */
    RUNTIME;

    /**
     * Parses an engine name, tolerating case and surrounding whitespace. Returns {@code null} for
     * an empty/blank input (meaning "unset"); throws a typed {@link ConfigurationException} for any
     * other unrecognized value so misconfiguration fails loudly with a usable message.
     */
    public static Engine fromString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Engine.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(
                    ErrorCode.CONFIG_PARSE_ERROR,
                    "Invalid DI engine '" + trimmed + "'. Use 'runtime' or 'aot'.");
        }
    }
}
