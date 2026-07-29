package com.github.dropguard.summer.tck;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;

/**
 * Structural invariant enforcing the Quarkus-style disjoint-pipeline model.
 *
 * <p>Sad-path (negative) fixtures under the errors package are intentionally broken beans used only
 * by narrow @SummerTest(classes=...) tests, which reach them via NarrowIndexBuilder reading their
 * .class bytes directly from the classpath (Quarkus ArcTestContainer beanClasses model). They must
 * never enter the whole-universe @SummerTest container, which indexes exactly the running test
 * class's OWN test-classes directory (Quarkus @QuarkusTest model, via TestClassIndexer) -- a
 * separate module's test-classes are simply not on that path. If they did leak in, a single broken
 * graph would poison every healthy test.
 *
 * <p>The boundary is structural, not a path glob or an exclude list: the negative fixtures live in
 * the dedicated summer-tck-negative-fixtures module, which ships no jandex.idx and is not on the
 * path of this module's test-classes directory. This test locks that contract at the level that
 * actually matters -- the beans the whole-universe container registers -- so a regression (e.g.
 * someone Jandexing the negative module, or changing the test index to a bulk classpath sweep)
 * surfaces in CI instead of as silent contamination.
 */
class NegativeFixtureIsolationTest {

    private static final String ERRORS_PACKAGE =
            "com.github.dropguard.summer.tck.negative.fixtures.di";

    @Test
    void negativeFixturesAreNotInWholeUniverseContainer() {
        // Whole-universe build from THIS test class: indexes only summer-tck's own
        // test-classes directory, never the negative-fixtures module.
        BeanContainer ctx = TestContainer.builder().testClass(getClass()).build();

        for (String type :
                new String[] {
                    "CycleNodeA", "NeedsMissingDep", "SelfInjectingBean", "AmbiguousServiceImplOne"
                }) {
            Class<?> fixture;
            try {
                fixture = Class.forName(ERRORS_PACKAGE + "." + type);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(
                        "Negative fixture " + type + " must be on the classpath for narrow tests",
                        e);
            }
            assertFalse(
                    ctx.containsBean(fixture),
                    "Negative fixture "
                            + type
                            + " must not be registered in the whole-universe container.");
        }
    }

    @Test
    void negativeFixturesRemainReachableByClasspathForNarrowTests() {
        // Narrow tests rely on the .class bytes being loadable from the classpath
        // (NarrowIndexBuilder indexes them directly). This guards against an
        // over-zealous exclusion that would also strip them from the classpath.
        try {
            Class.forName(ERRORS_PACKAGE + ".CycleNodeA");
            Class.forName(ERRORS_PACKAGE + ".NeedsMissingDep");
        } catch (ClassNotFoundException e) {
            assertFalse(
                    true,
                    "Negative fixture .class bytes must remain on the classpath for narrow tests: "
                            + e);
        }
    }
}
