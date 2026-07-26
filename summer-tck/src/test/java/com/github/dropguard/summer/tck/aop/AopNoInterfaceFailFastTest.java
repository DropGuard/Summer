package com.github.dropguard.summer.tck.aop;

import com.github.dropguard.summer.tck.negative.fixtures.aop.errors.AopMarker;
import com.github.dropguard.summer.tck.negative.fixtures.aop.errors.MarkerInterceptor;
import com.github.dropguard.summer.tck.negative.fixtures.aop.errors.NoInterfaceBoundBean;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Dual-engine (Runtime + AOT) contract: a bean annotated with an
 * {@code @InterceptorBinding} but implementing no interface must fail assembly
 * with {@code SummerAopException} / {@code ErrorCode.AOP_NO_INTERFACE}.
 *
 * <p>
 * This pins the framework's fail-fast behaviour so a future refactor cannot
 * silently downgrade it to "proxy skipped, binding dead" — which would make
 * {@code @Transactional} appear to do nothing on a class-based bean.
 * </p>
 */
@SummerTest(classes = {NoInterfaceBoundBean.class, MarkerInterceptor.class, AopMarker.class}, shouldFail = true)
public class AopNoInterfaceFailFastTest {

	@DualEngine
	void noInterfaceBindingRejected() {
		// Assembly is expected to fail; the empty body is the success condition.
	}
}
