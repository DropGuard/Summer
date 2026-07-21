package summer.tck.di;

import summer.tck.fixtures.di.errors.CycleNodeA;
import summer.tck.fixtures.di.errors.CycleNodeB;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

/**
 * Dual-engine (Runtime + AOT) contract: a circular dependency must be detected
 * at assembly, not silently wired. The broken graph is isolated via
 * {@code classes=...} (seed + transitive closure) and the build is declared
 * expected-to-fail with {@code shouldFail=true} — Quarkus'
 * {@code ArcTestContainer.shouldFail} model. {@code @DualEngine} judges each
 * engine independently, so a divergence surfaces as a per-engine failure.
 */
@SummerTest(classes = {CycleNodeA.class, CycleNodeB.class}, shouldFail = true)
public class CircularDependencyBehaviorTest {

	@DualEngine
	void cycleDetected() {
	}
}
