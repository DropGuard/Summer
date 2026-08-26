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

    @WithDefault("RADIX_TREE")
    RouterType routerType();
}
