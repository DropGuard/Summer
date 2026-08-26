package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.websocket.WebSocketBroadcaster;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty-based WebSocket broadcaster. Per-instance by design — see the interface javadoc for the
 * delivery-scope contract and the multi-instance upgrade path.
 *
 * <p>Thread-safety: {@code roomGroups} is a {@link ConcurrentHashMap} because concurrent websocket
 * connections may join different rooms at once — CHM.computeIfAbsent guarantees one group per room
 * and makes the read path lock-free. This is an explicit class-level exception to the project's
 * usual ConcurrentHashMap ban — see {@code ArchitectureTest#noConcurrentHashMap}.
 */
@Internal
public class NettyWebSocketBroadcaster implements WebSocketBroadcaster {

    private final ChannelGroup liveSessions = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final Map<String, ChannelGroup> roomGroups = new ConcurrentHashMap<>();

    /**
     * Rooms with no members are evicted immediately on leave — dynamic room names (e.g. {@code
     * issue-123}) must not accumulate empty groups. Rejoining simply creates a fresh group.
     */
    @Override
    public void connected(WebSocketContext ctx) {
        liveSessions.add(requireNettyContext(ctx).getChannelHandlerContext().channel());
    }

    @Override
    public void join(String room, WebSocketContext ctx) {
        roomGroups
                .computeIfAbsent(room, k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE))
                .add(requireNettyContext(ctx).getChannelHandlerContext().channel());
    }

    @Override
    public void leave(String room, WebSocketContext ctx) {
        var channel = requireNettyContext(ctx).getChannelHandlerContext().channel();
        // remove(key, value) guards against evicting a group that was concurrently
        // recreated for a new member under the same name.
        roomGroups.computeIfPresent(
                room,
                (k, group) -> {
                    group.remove(channel);
                    return group.isEmpty() ? null : group;
                });
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
        liveSessions.writeAndFlush(new TextWebSocketFrame(message));
    }

    /** Number of rooms currently holding at least one member. */
    int trackedRoomCount() {
        return roomGroups.size();
    }

    private static NettyWebSocketContext requireNettyContext(WebSocketContext ctx) {
        if (!(ctx instanceof NettyWebSocketContext nettyCtx)) {
            throw new IllegalArgumentException(
                    "Unsupported WebSocketContext implementation: "
                            + ctx.getClass().getName()
                            + " — use the transport-provided context");
        }
        return nettyCtx;
    }
}
