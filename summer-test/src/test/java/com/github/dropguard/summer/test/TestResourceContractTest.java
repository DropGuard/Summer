package com.github.dropguard.summer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The test-infrastructure's own behavior contracts — the granularity that was missing when the
 * RedisTestResource's env-style key silently fell back to @WithDefault and the IT layer silently
 * skipped. Each contract pins one behavior of the TestResource machinery:
 *
 * <ol>
 *   <li>initArgs reach the resource before start().
 *   <li>The dotted-key overrides reach a @ConfigMapping's binding (not the env-style form).
 *   <li>inject() fills the test's fields after the constructor injection.
 * </ol>
 */
@SummerTest
@TestResource(value = FakeConfigResource.class, initArgs = "probe=init-arg-value")
public class TestResourceContractTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder().beanClasses(FakeProbeProps.class).build();

    private final FakeProbeProps props;

    // Filled by FakeConfigResource.inject() — the second channel beside the config overrides.
    private String injected;

    public TestResourceContractTest(FakeProbeProps props) {
        this.props = props;
    }

    @Test
    void initArgsReachTheResourceBeforeStart() {
        assertEquals("init-arg-value", FakeConfigResource.seenInitArg);
        assertTrue(FakeConfigResource.started);
    }

    @Test
    void dottedKeyOverridesReachTheConfigMapping() {
        assertEquals("init-arg-value", props.key());
        assertEquals("from-resource", props.injected());
    }

    @Test
    void resourceInjectionFillsTheTestFields() {
        assertEquals("injected-value", injected);
    }
}
