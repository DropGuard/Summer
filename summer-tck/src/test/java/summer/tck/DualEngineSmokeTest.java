package summer.tck;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import summer.fixtures.dummy.ServiceA;
import summer.fixtures.dummy.ServiceB;
import summer.fixtures.dummy.ServiceC;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

/**
 * Acceptance gate for the test-infra refactor: proves a single
 * {@code @SummerTest} method is executed on BOTH DI engines (Runtime + AOT)
 * with identical results.
 *
 * <p>
 * A single {@code @SummerTest} method is executed on BOTH DI engines (Runtime +
 * AOT) with identical results. The {@code @DualEngine} method triggers
 * dual-engine execution: the container is the full test universe (whole
 * application plus test beans from {@code summer-tck-fixtures}), so the
 * dependency chain {@code ServiceA → ServiceB → ServiceC} must resolve
 * identically on both engines. If the two engines diverge, this test reports a
 * per-engine failure rather than a silent pass.
 * </p>
 */

@SummerTest
class DualEngineSmokeTest {

	ServiceA serviceA;

	public DualEngineSmokeTest(ServiceA serviceA) {
		this.serviceA = serviceA;
	}

	@DualEngine
	void injectsFullDependencyChain() {
		assertNotNull(serviceA, "ServiceA should be injected");
		ServiceB b = serviceA.getServiceB();
		assertNotNull(b, "ServiceB (dependency of ServiceA) should be injected");
		ServiceC c = b.getServiceC();
		assertNotNull(c, "ServiceC (dependency of ServiceB) should be injected");
	}
}
