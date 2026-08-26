package com.github.dropguard.summer.test;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.test.FakeOrderedResources.High;
import com.github.dropguard.summer.test.FakeOrderedResources.Low;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the TestResources cache contract across test classes: the static cache maps every {@code
 * ResourceKey} to the entry of ITS OWN resource, regardless of declaration order versus {@code
 * order()} — and a later class declaring a SUBSET must never receive another resource's cached
 * entry (the S-07 cross-contamination regression).
 */
class TestResourcesCacheContractTest {

    /** Declaration order deliberately REVERSED relative to order() — the swap trigger. */
    @com.github.dropguard.summer.test.annotation.TestResource(High.class)
    @com.github.dropguard.summer.test.annotation.TestResource(Low.class)
    static class Descending {}

    @com.github.dropguard.summer.test.annotation.TestResource(Low.class)
    static class OnlyLow {}

    @BeforeEach
    void resetCache() {
        TestResources.shutdown();
    }

    @Test
    void cacheKeysSurviveTheOrderSort() {
        List<TestResources.Entry> first = TestResources.startAllForClass(Descending.class);

        assertEquals(2, first.size());
        assertInstanceOf(Low.class, first.get(0).instance(), "order(0) starts first");
        assertInstanceOf(High.class, first.get(1).instance());
        assertTrue(first.get(0).started());
        assertTrue(first.get(1).started());

        // The poison check: each key's cached entry must be its own resource.
        List<TestResources.Entry> again = TestResources.startAllForClass(Descending.class);
        assertInstanceOf(
                Low.class,
                again.get(0).instance(),
                "re-declaration with reversed order must resolve Low for the Low slot");
        assertInstanceOf(High.class, again.get(1).instance());
    }

    @Test
    void subsetDeclarationMustNotReceiveAnotherResourcesEntry() {
        TestResources.startAllForClass(Descending.class); // trigger the swap precondition

        List<TestResources.Entry> second = TestResources.startAllForClass(OnlyLow.class);

        assertEquals(1, second.size());
        assertInstanceOf(
                Low.class,
                second.get(0).instance(),
                "a class declaring only Low must get the Low instance — receiving "
                        + "the High cached entry means the cache keys crossed");
        assertTrue(second.get(0).started());
    }
}
