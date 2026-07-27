package com.github.dropguard.summer.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConfigBinder}'s {@code ${VAR}} / {@code ${VAR:-default}} placeholder
 * resolution. The live environment lookup itself (System.getenv / System.getProperty) cannot be
 * mutated from within the JVM, so these tests lock the parsing and default-fallback behavior; the
 * actual env override is exercised by the demo's externalized datasource URL.
 */
class ConfigBinderEnvTest {

    @Test
    void fallsBackToDefaultWhenEnvAbsent() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("url", "${SUMMER_DB_URL:-jdbc:postgresql://localhost:5432/db}");
        Map<String, Object> resolved = ConfigBinder.resolveEnvPlaceholders(section);
        assertEquals("jdbc:postgresql://localhost:5432/db", resolved.get("url"));
    }

    @Test
    void fallsBackToDefaultWithColonForm() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("url", "${SUMMER_DB_URL:jdbc:fallback}");
        Map<String, Object> resolved = ConfigBinder.resolveEnvPlaceholders(section);
        assertEquals("jdbc:fallback", resolved.get("url"));
    }

    @Test
    void barePlaceholderDegradesToOriginalWhenEnvAbsent() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("url", "${SUMMER_DB_URL}");
        Map<String, Object> resolved = ConfigBinder.resolveEnvPlaceholders(section);
        assertEquals("${SUMMER_DB_URL}", resolved.get("url"));
    }

    @Test
    void leavesLiteralValuesUntouched() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("url", "jdbc:postgresql://localhost:5432/db");
        section.put("name", "issuetracker");
        Map<String, Object> resolved = ConfigBinder.resolveEnvPlaceholders(section);
        assertEquals("jdbc:postgresql://localhost:5432/db", resolved.get("url"));
        assertEquals("issuetracker", resolved.get("name"));
    }

    @Test
    void resolvesNestedSections() {
        Map<String, Object> section = new LinkedHashMap<>();
        Map<String, Object> datasource = new LinkedHashMap<>();
        datasource.put("url", "${SUMMER_DB_URL:-jdbc:default}");
        section.put("datasource", datasource);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("summer", section);
        Map<String, Object> resolvedRoot = ConfigBinder.resolveEnvPlaceholders(root);
        @SuppressWarnings("unchecked")
        Map<String, Object> resolved = (Map<String, Object>) resolvedRoot.get("summer");
        @SuppressWarnings("unchecked")
        Map<String, Object> ds = (Map<String, Object>) resolved.get("datasource");
        assertEquals("jdbc:default", ds.get("url"));
    }

    @Test
    void resolvesMultiplePlaceholdersInOneValue() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("ds", "${A:-x}-${B:-y}");
        Map<String, Object> resolved = ConfigBinder.resolveEnvPlaceholders(section);
        assertEquals("x-y", resolved.get("ds"));
    }
}
