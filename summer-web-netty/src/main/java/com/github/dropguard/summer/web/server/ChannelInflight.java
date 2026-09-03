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
 *
 * <p>The flag doubles as the connection's pipelining gate. HTTP/1.1 responses must remain ordered
 * with requests (RFC 9112 §7.6) and each request is dispatched to its own virtual thread, so a
 * second request arriving while the flag is set cannot be answered without risking response
 * interleaving. The handler closes the connection in that case — the honest protocol-level answer,
 * which also caps per-connection resource use at one in-flight request plus the TCP read buffer.
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
