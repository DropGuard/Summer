package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.websocket.WebSocketBroadcaster;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.HashMap;

/**
 * Netty-based WebSocket broadcaster.
 *
 * <p>This is a framework infrastructure bean provided by {@link RuntimeWebConfiguration}.
 */
public class NettyWebSocketBroadcaster implements WebSocketBroadcaster {

    private final ChannelGroup globalGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final HashMap<String, ChannelGroup> roomGroups = new HashMap<>();

    @Override
    public void join(String room, WebSocketContext ctx) {
        if (ctx instanceof NettyWebSocketContext nCtx) {
            ChannelGroup group =
                    roomGroups.computeIfAbsent(
                            room, k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
            group.add(nCtx.getChannelHandlerContext().channel());
            // Automatically add to global group for broadcastAll to work seamlessly
            globalGroup.add(nCtx.getChannelHandlerContext().channel());
        }
    }

    @Override
    public void leave(String room, WebSocketContext ctx) {
        if (ctx instanceof NettyWebSocketContext nCtx) {
            ChannelGroup group = roomGroups.get(room);
            if (group != null) {
                group.remove(nCtx.getChannelHandlerContext().channel());
            }
            globalGroup.remove(nCtx.getChannelHandlerContext().channel());
        }
    }

    @Override
    public void broadcast(String room, String message) {
        ChannelGroup group = roomGroups.get(room);
        if (group != null) {
            group.writeAndFlush(new TextWebSocketFrame(message));
        }
    }

    @Override
    public void broadcastAll(String message) {
        globalGroup.writeAndFlush(new TextWebSocketFrame(message));
    }
}
