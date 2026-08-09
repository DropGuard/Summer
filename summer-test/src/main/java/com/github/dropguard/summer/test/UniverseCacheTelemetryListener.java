package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.Internal;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * ServiceLoader-registered suite-end listener that surfaces the universe-cache telemetry. It must
 * be a separate class: the ServiceLoader contract requires a public no-arg constructor, which the
 * {@link SummerTestLifecycle} singleton cannot offer (its constructor is private). The listener
 * just delegates to the singleton, so the cache state stays in one place.
 */
@Internal
public final class UniverseCacheTelemetryListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        SummerTestLifecycle.instance().logCacheTelemetry();
    }
}
