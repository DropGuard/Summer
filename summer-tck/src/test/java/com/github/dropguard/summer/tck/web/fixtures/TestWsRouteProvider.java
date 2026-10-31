package com.github.dropguard.summer.tck.web.fixtures;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.WsRouteProvider;
import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import com.github.dropguard.summer.web.websocket.WebSocketInterceptor;
import com.github.dropguard.summer.web.websocket.WebSocketInterceptorChain;

/**
 * WebSocket route provider for the interceptor integration test. Registered as a {@code @Component}
 * (not a {@code @Bean}) so its bean name is the concrete class — multiple {@code WsRouteProvider}
 * implementations can coexist in the test universe and are all collected by {@code
 * NettyServerRunner}.
 *
 * <p>The prefixing interceptor is ROUTE-level ({@code Builder.intercept}): rewriting payloads is a
 * per-route concern and only route-level interceptors have that power.
 */
@Component
public class TestWsRouteProvider implements WsRouteProvider {

    @Override
    public void provide(WsRouter.Builder builder) {
        builder.intercept(
                "/ws-test",
                new WebSocketInterceptor() {
                    @Override
                    public void intercept(
                            WebSocketContext ctx, String message, WebSocketInterceptorChain chain) {
                        chain.proceed(ctx, "[INTERCEPTED] " + message);
                    }
                });
        builder.ws(
                "/ws-test",
                ctx -> {
                    ctx.onMessage(
                            msg -> {
                                ctx.send(msg);
                            });
                });
    }
}
