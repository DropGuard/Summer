package com.github.dropguard.summer.tck.web;

import com.github.dropguard.summer.fixtures.aop.Logged;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.ExceptionHandler;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;

/**
 * Fixture: an AOP-bound controller (its {@code @Logged} methods bind it to {@code
 * RecordingInterceptor}) carrying BOTH a route and an {@code @ExceptionHandler}.
 *
 * <p>Under the one-bean-one-form contract this bean exists in the lookup plane only as its proxy,
 * and both the route and the exception handler are framework registration hooks that must consume
 * the bean's single legal incarnation and dispatch through the proxy — otherwise registration fails
 * loudly at startup (getBean on the concrete class) or silently bypasses interception. The route
 * and the handler methods are declared on {@link ProxiedGatewayApi}: interface-based proxies can
 * only dispatch interface methods, so the interface declaration is part of the contract.
 */
@RestController
public class ProxiedGatewayController implements ProxiedGatewayApi {

    @Override
    @Logged
    @Get("/proxied/boom")
    public void throwState(HttpContext ctx) {
        throw new IllegalStateException("kaboom");
    }

    @Override
    @Logged
    @ExceptionHandler(IllegalStateException.class)
    public void onState(IllegalStateException ex, HttpContext ctx) {
        ctx.text(HttpStatus.BAD_REQUEST, "proxied_caught:" + ex.getMessage());
    }
}
