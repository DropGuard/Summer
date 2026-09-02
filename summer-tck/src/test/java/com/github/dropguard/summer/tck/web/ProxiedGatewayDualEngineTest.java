package com.github.dropguard.summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.fixtures.aop.RecordingInterceptor;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.web.HttpMethod;

/**
 * TCK: an AOP-bound controller's route AND {@code @ExceptionHandler} must both dispatch through the
 * bean's single legal incarnation (the proxy) on BOTH engines.
 *
 * <p>This is the regression test for the one-bean-one-form contract gap: registration used to
 * resolve the controller/handler via {@code getBean(ConcreteClass)}, which fails loudly for bound
 * beans (startup crash) — and had it "worked", would have bypassed interception. The interceptor
 * log assertions pin the deeper half: interception must APPLY to both the route call and the
 * exception-handler dispatch.
 */
@SummerTest
public class ProxiedGatewayDualEngineTest extends AbstractWebRouteTCK {

    @DualEngine
    protected void proxiedRouteAndHandlerDispatchThroughProxy() throws Exception {
        RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);
        interceptor.clearLog();

        // Route call throws -> dispatch catch -> registry -> handler response — the same path
        // NettyHttpServerHandler runs, exercising registration-time birth-record consumption and
        // proxy dispatch for both the route and the exception handler.
        assertEquals(
                "proxied_caught:kaboom",
                dispatchWithExceptionMapping(HttpMethod.GET, "/proxied/boom"));

        assertTrue(
                interceptor.getCallLog().contains("before:throwState"),
                "the route call must be intercepted (dispatch through the proxy): "
                        + interceptor.getCallLog());
        assertTrue(
                interceptor.getCallLog().contains("before:onState"),
                "the exception-handler dispatch must be intercepted too (dispatch through the"
                        + " proxy): "
                        + interceptor.getCallLog());
    }
}
