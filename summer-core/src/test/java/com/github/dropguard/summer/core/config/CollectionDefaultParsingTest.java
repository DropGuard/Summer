package com.github.dropguard.summer.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.exception.ConfigurationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigBinder#parseCollectionDefault} is the single shared implementation for
 * collection-typed {@code @WithDefault} values — the Runtime proxy and the AOT-generated impl both
 * funnel through it, so both engines must produce the same shapes: YAML flow literals with YAML
 * element typing, the Spring comma convention for string lists, and loud failures everywhere else
 * (the previous behavior silently substituted an empty collection).
 */
class CollectionDefaultParsingTest {

    @Test
    void yamlFlowListParsesWithYamlElementTyping() {
        Object parsed = ConfigBinder.parseCollectionDefault("[eu-west, us-east]", false);
        assertEquals(List.of("eu-west", "us-east"), parsed);

        Object numbers = ConfigBinder.parseCollectionDefault("[1, 2]", false);
        assertEquals(List.of(1, 2), numbers);
    }

    @Test
    void commaConventionYieldsTrimmedStringList() {
        assertEquals(
                List.of("a", "b", "c"), ConfigBinder.parseCollectionDefault("a, b , ,c", false));
        assertEquals(List.of(), ConfigBinder.parseCollectionDefault("  ", false));
    }

    @Test
    void yamlFlowMapParses() {
        Object parsed = ConfigBinder.parseCollectionDefault("{k: v, n: 1}", true);
        assertEquals(Map.of("k", "v", "n", 1), parsed);
    }

    @Test
    void mapWithoutBracesIsLoud() {
        ConfigurationException ex =
                assertThrows(
                        ConfigurationException.class,
                        () -> ConfigBinder.parseCollectionDefault("k=v", true));
        assertTrue(ex.getMessage().contains("YAML flow syntax"));
    }

    @Test
    void shapeMismatchIsLoud() {
        assertThrows(
                ConfigurationException.class,
                () -> ConfigBinder.parseCollectionDefault("[a, b]", true));
        assertThrows(
                ConfigurationException.class,
                () -> ConfigBinder.parseCollectionDefault("{k: v}", false));
    }
}
