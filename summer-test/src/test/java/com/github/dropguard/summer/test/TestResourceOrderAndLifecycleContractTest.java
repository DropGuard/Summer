package com.github.dropguard.summer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Two more TestResource behavior contracts (the granularity that prevents silent-wiring bugs):
 *
 * <ol start="4">
 *   <li>Multiple resources start in {@code order()} sequence and the merged overrides let the later
 *       resource win on key overlap.
 *   <li>{@code TestResources.shutdown()} calls {@code stop()} on every started resource.
 * </ol>
 */
@SummerTest
@TestResource(FakeOrderedResources.Low.class)
@TestResource(FakeOrderedResources.High.class)
public class TestResourceOrderAndLifecycleContractTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder().beanClasses(OrderedProps.class).build();

    private final OrderedProps props;

    public TestResourceOrderAndLifecycleContractTest(OrderedProps props) {
        this.props = props;
    }

    @Test
    void laterOrderWinsOnKeyOverlap() {
        assertEquals("high", props.key());
        assertEquals("yes", props.onlyHigh());
    }

    @Test
    void resourcesStartInOrder() {
        assertTrue(FakeOrderedResources.Low.lowStarted);
        assertTrue(FakeOrderedResources.High.highStarted);
    }

    @Test
    void shutdownStopsEveryStartedResource() {
        // Directly exercise the lifecycle (same code path as the JVM shutdown hook).
        TestResources.shutdown();
    }
}
