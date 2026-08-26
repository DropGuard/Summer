package com.github.dropguard.summer.tck.web;

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

    @DualEngine
    void methodLevelMiddleware() throws Exception {
        super.testMethodLevelMiddleware();
    }

    @DualEngine
    void classLevelMiddleware() throws Exception {
        super.testClassLevelMiddleware();
    }

    @DualEngine
    void multipleMiddlewares() throws Exception {
        super.testMultipleMiddlewares();
    }

    @DualEngine
    void globalMiddlewareAppliedToAllRoutes() throws Exception {
        super.testGlobalMiddlewareAppliedToAllRoutes();
    }
}
