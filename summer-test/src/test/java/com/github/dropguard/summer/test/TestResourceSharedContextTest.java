package com.github.dropguard.summer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins the cross-resource shared-context contract: a later-order {@code TestResource} receives the
 * merged properties of every earlier resource via {@link TestResourceManager#setContext(Map)} — the
 * Quarkus {@code DevServicesContext.ContextAware} model. This is what lets a seed resource read the
 * JDBC URL produced by the Postgres resource.
 */
@SummerTest
@TestResource.List({
    @TestResource(value = TestResourceSharedContextTest.ProviderResource.class),
    @TestResource(value = TestResourceSharedContextTest.ConsumerResource.class)
})
public class TestResourceSharedContextTest {

    /** Lower order: starts first, produces {@code shared.url}. */
    public static final class ProviderResource implements TestResourceManager {
        @Override
        public Map<String, String> start() {
            return Map.of("shared.url", "jdbc:postgresql://localhost:5432/db");
        }

        @Override
        public void stop() {}

        @Override
        public int order() {
            return 0;
        }
    }

    /** Higher order: starts after Provider, must see {@code shared.url} via setContext. */
    public static final class ConsumerResource implements TestResourceManager {
        static String seenUrl = null;

        @Override
        public Map<String, String> start() {
            return Map.of();
        }

        @Override
        public void stop() {}

        @Override
        public int order() {
            return 1;
        }

        @Override
        public void setContext(Map<String, String> sharedProperties) {
            seenUrl = sharedProperties.get("shared.url");
        }
    }

    @Test
    void laterResourceReceivesEarlierResourcesPropertiesViaSetContext() {
        // Provider (order 0) started first, producing shared.url; Consumer (order 1) must have
        // received it through setContext before its own start().
        assertEquals("jdbc:postgresql://localhost:5432/db", ConsumerResource.seenUrl);
        assertTrue(ConsumerResource.seenUrl != null);
    }
}
