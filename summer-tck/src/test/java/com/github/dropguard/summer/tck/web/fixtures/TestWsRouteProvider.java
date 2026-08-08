package com.github.dropguard.summer.tck.web.fixtures;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.WsRouteProvider;
import com.github.dropguard.summer.web.WsRouter;

/**
 * WebSocket route provider for the interceptor integration test. Registered as a {@code @Component}
 * (not a {@code @Bean}) so its bean name is the concrete class — multiple {@code WsRouteProvider}
 * implementations can coexist in the test universe and are all collected by {@code
 * NettyServerRunner}.
 */
@Component
public class TestWsRouteProvider implements WsRouteProvider {

    @Override
    public void provide(WsRouter.Builder builder) {
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
