package com.github.dropguard.summer.tck.web;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Middleware wiring TCK.
 *
 * <p>Middleware-chain behaviour is verified on BOTH DI engines via {@link DualEngine} (the
 * framework-enforced parity guarantee) — every case from {@link AbstractMiddlewareTCK} is exercised
 * on Runtime and AOT. The previous {@code RuntimeMiddlewareTest} / {@code AotMiddlewareTest}
 * siblings, which only switched the engine through {@code createContext()}, are obsolete.
 */
@SummerTest
public class MiddlewareBehaviorTest extends AbstractMiddlewareTCK {

    public MiddlewareBehaviorTest(BeanContainer context) {
        super(context);
    }

    @DualEngine
    void methodLevelMiddleware() {
        super.testMethodLevelMiddleware();
    }

    @DualEngine
    void classLevelMiddleware() {
        super.testClassLevelMiddleware();
    }

    @DualEngine
    void multipleMiddlewares() {
        super.testMultipleMiddlewares();
    }

    @DualEngine
    void globalMiddlewareAppliedToAllRoutes() {
        super.testGlobalMiddlewareAppliedToAllRoutes();
    }
}
