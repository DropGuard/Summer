package com.github.dropguard.summer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.core.exception.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies engine resolution. The override path ({@code -Dsummer.engine}) is fully exercisable in a
 * core-only context because an explicit override short-circuits before any configuration is read.
 * The enum parsing ({@link Engine#fromString}) is the single source of truth for legal values and
 * is tested directly so the allowed set lives only in {@link Engine}.
 */
class DiEngineEngineResolutionTest {

    @AfterEach
    void clear() {
        System.clearProperty("summer.engine");
    }

    @Test
    void overrideRuntimeIsResolvedCaseInsensitively() {
        System.setProperty("summer.engine", "RUNTIME");
        assertEquals(Engine.RUNTIME, DiEngine.resolveEngine());
    }

    @Test
    void overrideAotIsResolved() {
        System.setProperty("summer.engine", "aot");
        assertEquals(Engine.AOT, DiEngine.resolveEngine());
    }

    @Test
    void fromStringTreatsBlankAsUnset() {
        assertNull(Engine.fromString("   "));
        assertNull(Engine.fromString(null));
    }

    @Test
    void fromStringParsesCaseInsensitively() {
        assertEquals(Engine.AOT, Engine.fromString("AOT"));
        assertEquals(Engine.RUNTIME, Engine.fromString("runtime"));
    }

    @Test
    void fromStringRejectsUnknownValue() {
        ConfigurationException ex =
                assertThrows(ConfigurationException.class, () -> Engine.fromString("fast"));
        // The message must steer the user to the two legal values, proving the enum is the
        // authority and no raw string comparison leaked into the caller.
        assertEquals(true, ex.getMessage().contains("runtime") && ex.getMessage().contains("aot"));
    }
}
