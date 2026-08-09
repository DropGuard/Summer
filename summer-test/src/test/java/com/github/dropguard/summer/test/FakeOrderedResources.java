package com.github.dropguard.summer.test;

import java.util.Map;

/**
 * Two resources with different {@link #order()}s to contract-test the merge: the later-order
 * resource's properties win on key overlap. Shared static flags let the contract test observe the
 * start sequence.
 */
public final class FakeOrderedResources {

    private FakeOrderedResources() {}

    public static final class Low implements TestResourceManager {
        static final String KEY = "order.key";
        static volatile boolean lowStarted;

        @Override
        public Map<String, String> start() {
            lowStarted = true;
            return Map.of(KEY, "low");
        }

        @Override
        public void stop() {}
    }

    public static final class High implements TestResourceManager {
        static volatile boolean highStarted;

        @Override
        public int order() {
            return 10;
        }

        @Override
        public Map<String, String> start() {
            highStarted = true;
            return Map.of("order.key", "high", "order.only-high", "yes");
        }

        @Override
        public void stop() {}
    }
}
