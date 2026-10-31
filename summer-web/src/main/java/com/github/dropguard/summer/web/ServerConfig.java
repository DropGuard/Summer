package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;
import java.util.List;

/** Immutable server configuration bound from {@code application.yml}. */
@ConfigMapping(prefix = "server")
public interface ServerConfig {

    @WithDefault("8080")
    Integer port();

    @WithDefault("60000")
    Integer idleTimeout();

    @WithDefault("10485760")
    Integer maxBodySize();

    /**
     * Maximum time to receive one request (headers + body), measured from connection open. Once a
     * complete request has arrived it is never re-armed on this connection — slow-request
     * protection exists to bound per-connection resource hold during request reception, not to
     * police streaming responses or keep-alive gaps (see {@code idleTimeout}).
     */
    @WithDefault("10000")
    Integer requestReceiveTimeout();

    /**
     * Interval between server-initiated WebSocket ping frames (RFC 6455 control frames, invisible
     * to user handlers). A connection is closed when no pong arrives within twice the interval.
     * {@code 0} disables the heartbeat. This — not read-idle detection — is what keeps long-lived,
     * quiet WebSocket connections alive while still detecting dead peers.
     */
    @WithDefault("30000")
    Integer wsHeartbeatInterval();

    List<String> allowedOrigins();

    @WithDefault("65536")
    Integer maxWebSocketFrameSize();

    /**
     * Maximum number of inbound WebSocket messages buffered per connection while the user's message
     * handler is still working through earlier ones. Inbound reads are suspended once the buffer
     * reaches 3/4 of this and resumed at half, so a slow consumer turns into TCP backpressure
     * instead of unbounded memory growth. Frames already in flight that still overflow the buffer
     * close the connection with a 1008 (Policy Violation) close frame.
     */
    @WithDefault("64")
    Integer wsMessageQueueCapacity();

    /**
     * Maximum time a connection's outbound side may stay unwritable — the peer is not draining and
     * Netty's outbound buffer is pinned above the high watermark — before the connection is closed.
     * This bounds memory growth against slow consumers and reclaims zero-window peers that TCP
     * alone would keep for minutes. Blocking writes (WebSocket {@code send}, chunked/SSE writes)
     * are released by the same budget; the broadcaster never waits on a stalled member, the
     * watchdog closes it instead. Ordinary request/response writes are bounded by their own size
     * and are not policed. {@code 0} disables.
     */
    @WithDefault("10000")
    Integer outboundSendTimeout();

    @WithDefault("RADIX_TREE")
    RouterType routerType();
}
