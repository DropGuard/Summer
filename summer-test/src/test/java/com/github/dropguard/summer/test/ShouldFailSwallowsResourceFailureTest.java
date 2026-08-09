package com.github.dropguard.summer.test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.core.Engine;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A {@code @SummerTest(shouldFail=true)} test promises ASSEMBLY fails. A broken TestResource is an
 * infrastructure failure: it must surface even under shouldFail — otherwise a dead database makes a
 * negative test pass green.
 */
class ShouldFailSwallowsResourceFailureTest {

    static final class BrokenResource implements TestResourceManager {
        @Override
        public Map<String, String> start() {
            throw new IllegalStateException("database container cannot start");
        }

        @Override
        public void stop() {}
    }

    @Test
    void resourceStartupFailureSurfacesEvenUnderShouldFail() throws Exception {
        assertThrows(
                TestResourceStartupException.class,
                () ->
                        SummerTestLifecycle.createUniverse(
                                FixtureWithBrokenResource.class, Engine.RUNTIME));
    }

    @com.github.dropguard.summer.test.annotation.TestResource(BrokenResource.class)
    static final class FixtureWithBrokenResource {
        @org.junit.jupiter.api.extension.RegisterExtension
        static SummerTestExtension ext =
                SummerTestExtension.builder()
                        .beanClasses(BrokenResource.class)
                        .shouldFail()
                        .build();
    }
}
