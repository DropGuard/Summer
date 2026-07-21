package summer.tck.di;

import summer.tck.fixtures.di.errors.MissingDep;
import summer.tck.fixtures.di.errors.NeedsMissingDep;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

/**
 * Dual-engine (Runtime + AOT) contract: a bean whose dependency is never
 * registered must fail assembly (not inject null). Isolated via
 * {@code classes=...} and declared expected-to-fail with
 * {@code shouldFail=true}.
 *
 * <p>
 * Both {@code NeedsMissingDep} and its dependency {@code MissingDep} are listed
 * as seeds (Quarkus' {@code beanClasses} contract: the caller lists every bean
 * the test needs, with no automatic transitive closure). {@code MissingDep} is
 * intentionally <em>not</em> a {@code @Component}, so it is present in the
 * index as a known type but has no bean definition — which is precisely the
 * missing-dependency scenario the test asserts.
 * </p>
 */
@SummerTest(classes = {NeedsMissingDep.class, MissingDep.class}, shouldFail = true)
public class MissingDependencyBehaviorTest {

	@DualEngine
	void missingDependencyRejected() {
	}
}
