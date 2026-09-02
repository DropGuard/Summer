package com.github.dropguard.summer.tck.web;

import com.github.dropguard.summer.web.HttpContext;

/**
 * Fixture interface for {@link ProxiedGatewayController}: the route and the exception-handler
 * methods are declared here, because interface-based proxies can only dispatch interface methods.
 * Public — the generated AOT adapters and the JDK proxy invocation handler invoke these methods
 * from other packages.
 */
public interface ProxiedGatewayApi {

    void throwState(HttpContext ctx);

    void onState(IllegalStateException ex, HttpContext ctx);
}
