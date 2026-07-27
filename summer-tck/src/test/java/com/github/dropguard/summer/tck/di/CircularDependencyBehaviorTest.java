package com.github.dropguard.summer.tck.di;

import com.github.dropguard.summer.tck.negative.fixtures.di.errors.CycleNodeA;
import com.github.dropguard.summer.tck.negative.fixtures.di.errors.CycleNodeB;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Dual-engine (Runtime + AOT) contract: a circular dependency must be detected at assembly, not
 * silently wired. The broken graph is isolated via {@code classes=...} (seed + transitive closure)
 * and the build is declared expected-to-fail with {@code shouldFail=true} — Quarkus' {@code
 * ArcTestContainer.shouldFail} model. {@code @DualEngine} judges each engine independently, so a
 * divergence surfaces as a per-engine failure.
 */
@SummerTest(
        classes = {CycleNodeA.class, CycleNodeB.class},
        shouldFail = true)
public class CircularDependencyBehaviorTest {

    @DualEngine
    void cycleDetected() {}
}
