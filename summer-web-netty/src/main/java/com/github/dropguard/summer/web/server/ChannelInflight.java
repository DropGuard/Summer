package com.github.dropguard.summer.web.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

/**
 * Marks whether a channel currently has response work in flight (a request being handled, an open
 * SSE/chunked stream, or a WebSocket session).
 *
 * <p>Read-idle detection ({@code IdleStateHandler}) must not close channels in that state: server
 * push legitimately means long stretches without inbound reads. The flag is set when a complete
 * request arrives and cleared by whichever write path finishes the channel's current work — the
 * buffered-response sink, the chunked/SSE stream close, or (for WebSocket) never: after an upgrade
 * the connection stays marked for its lifetime and liveness is owned by the WebSocket heartbeat
 * instead.
 */
final class ChannelInflight {

    private static final AttributeKey<Boolean> ACTIVE =
            AttributeKey.valueOf("summer.responseInflight");

    private ChannelInflight() {}

    static void markActive(ChannelHandlerContext ctx) {
        ctx.channel().attr(ACTIVE).set(Boolean.TRUE);
    }

    static void markComplete(ChannelHandlerContext ctx) {
        ctx.channel().attr(ACTIVE).set(Boolean.FALSE);
    }

    static boolean isActive(Channel channel) {
        Boolean flag = channel.attr(ACTIVE).get();
        return flag != null && flag;
    }
}
