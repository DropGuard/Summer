package com.github.dropguard.summer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The named-field injection form targets exactly the declared field: a resource owning one value
 * (e.g. the JDBC URL) must not clobber every field of the shared type on the test instance —
 * constructor-injected String fields included.
 */
@com.github.dropguard.summer.test.annotation.TestResource(
        value = TestInjectorNamedFieldTest.NamedResource.class)
class TestInjectorNamedFieldTest {

    static final class NamedResource implements TestResourceManager {
        @Override
        public Map<String, String> start() {
            return Map.of();
        }

        @Override
        public void stop() {}

        @Override
        public void inject(TestInjector injector) {
            injector.injectIntoField(
                    "jdbc:postgresql://localhost:5432/db", "jdbcUrl", String.class);
        }
    }

    static final class WithTarget {
        String jdbcUrl;
        String name = "initial";
    }

    static final class WithoutTarget {
        String name = "initial";
    }

    @Test
    void injectIntoFieldTargetsOnlyTheNamedField() {
        WithTarget instance = new WithTarget();
        TestResources.injectInto(TestInjectorNamedFieldTest.class, instance);
        assertEquals("jdbc:postgresql://localhost:5432/db", instance.jdbcUrl);
        assertEquals("initial", instance.name, "other String fields must be untouched");
    }

    @Test
    void absentNamedFieldIsANoOp() {
        WithoutTarget instance = new WithoutTarget();
        TestResources.injectInto(TestInjectorNamedFieldTest.class, instance);
        assertEquals("initial", instance.name);
        // No jdbcUrl field — nothing to assert beyond "no exception".
    }
}
