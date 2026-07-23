package summer.tck.di;

import summer.tck.negative.fixtures.di.errors.SelfInjectingBean;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

/**
 * Dual-engine (Runtime + AOT) contract: injecting the container into a bean
 * must be rejected at assembly. On Runtime this surfaces as a
 * {@code BeanCreationException}; on AOT the generated container fails to
 * compile — both are build failures, so {@code shouldFail=true} asserts the
 * contract on each engine independently via {@code @DualEngine}.
 */
@SummerTest(classes = {SelfInjectingBean.class}, shouldFail = true)
public class SelfInjectionBehaviorTest {

	@DualEngine
	void selfInjectionRejected() {
	}
}
