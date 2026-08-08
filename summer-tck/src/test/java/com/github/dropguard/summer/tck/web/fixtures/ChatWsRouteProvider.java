package com.github.dropguard.summer.tck.web.fixtures;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.WsRouteProvider;
import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.server.NettyWebSocketBroadcaster;

/**
 * WebSocket route provider for the broadcaster / HTTP-middleware integration tests. Registered as a
 * {@code @Component} (not a {@code @Bean}) so its bean name is the concrete class — multiple {@code
 * WsRouteProvider} implementations can coexist in the test universe and are all collected by {@code
 * NettyServerRunner}.
 */
@Component
public class ChatWsRouteProvider implements WsRouteProvider {

    private final NettyWebSocketBroadcaster broadcaster;

    public ChatWsRouteProvider(NettyWebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void provide(WsRouter.Builder builder) {
        builder.ws(
                "/chat/{room}",
                ctx -> {
                    String room = ctx.pathParam("room");
                    broadcaster.join(room, ctx);

                    ctx.onMessage(
                            msg -> {
                                int idx = msg.indexOf("BROADCAST:");
                                if (idx >= 0) {
                                    broadcaster.broadcast(
                                            room, msg.substring(idx + "BROADCAST:".length()));
                                }
                            });

                    ctx.onClose(
                            () -> {
                                broadcaster.leave(room, ctx);
                            });
                });
    }
}
