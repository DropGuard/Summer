package com.github.dropguard.summer.tck.web;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.Test;

/**
 * Web routing TCK.
 *
 * <p>Routing behaviour is verified on BOTH DI engines via {@link DualEngine} (the
 * framework-enforced parity guarantee) — every case from {@link
 * AbstractWebRouteTCK#routeTestCases()} is exercised on Runtime and AOT. The previous {@code
 * AotWebRouteTest} sibling, which only switched the engine through {@code createContext()}, is
 * obsolete. Exception-handler behaviour does not depend on engine wiring, so a plain {@code @Test}
 * (Runtime) is sufficient.
 */
@SummerTest
public class RuntimeWebRouteTest extends AbstractWebRouteTCK {

    public RuntimeWebRouteTest(BeanContainer context) {
        super(context);
    }

    @DualEngine
    protected void routingIsIdenticalAcrossEngines() {
        super.routeBehaviour();
    }

    @Test
    protected void exceptionHandlerBehaviour() {
        super.exceptionHandlerBehaviour();
    }
}
