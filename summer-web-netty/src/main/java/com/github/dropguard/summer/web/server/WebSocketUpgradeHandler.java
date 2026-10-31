package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.BodyConverter;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.ServerConfig;
import com.github.dropguard.summer.web.ServerOriginChecker;
import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.websocket.WebSocketBroadcaster;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WebSocketUpgradeHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketUpgradeHandler.class);

    private final WsRouter wsRouter;
    private final ServerConfig config;
    private final ServerOriginChecker serverOriginChecker;
    private final BodyConverter jsonConverter;
    private final WebSocketBroadcaster broadcaster;

    public WebSocketUpgradeHandler(
            WsRouter wsRouter,
            ServerConfig config,
            ServerOriginChecker serverOriginChecker,
            BodyConverter jsonConverter,
            WebSocketBroadcaster broadcaster) {
        this.wsRouter = wsRouter;
        this.config = config;
        this.serverOriginChecker = serverOriginChecker;
        this.jsonConverter = jsonConverter;
        this.broadcaster = broadcaster;
    }

    public boolean isWebSocketUpgrade(FullHttpRequest nettyReq) {
        return nettyReq.headers()
                .contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true);
    }

    public void handleUpgrade(
            ChannelHandlerContext nettyCtx, FullHttpRequest nettyReq, HttpContext c)
            throws Exception {
        String uri = nettyReq.uri();
        String path = uri;
        int questionMarkIndex = uri.indexOf('?');
        if (questionMarkIndex != -1) {
            path = uri.substring(0, questionMarkIndex);
        }

        String origin = nettyReq.headers().get(HttpHeaderNames.ORIGIN);
        String host = nettyReq.headers().get(HttpHeaderNames.HOST);
        if (!serverOriginChecker.isOriginAllowed(origin, host)) {
            log.warn(
                    "WebSocket connection rejected: origin '{}' not allowed for host '{}'",
                    origin,
                    host);
            c.status(HttpStatus.FORBIDDEN);
            c.text(HttpStatus.FORBIDDEN, "Origin not allowed");
            return;
        }

        WsRouter.WsMatch wsMatch = wsRouter.routeWs(path);
        if (wsMatch == null) {
            return;
        }

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> entry : nettyReq.headers()) {
            headers.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        // Interceptors are route-scoped only (WsRouter.Builder.intercept), in registration order.
        NettyWebSocketContext wsContext =
                new NettyWebSocketContext(
                        nettyCtx,
                        wsMatch.pathParams(),
                        headers,
                        wsMatch.interceptors(),
                        jsonConverter,
                        config.outboundSendTimeout());
        WebSocketInboundPump pump =
                new WebSocketInboundPump(
                        wsContext, nettyCtx.channel(), config.wsMessageQueueCapacity());
        // Register the live session before any user lifecycle code runs — this is
        // what makes broadcastAll cover every connection, joined or not.
        broadcaster.connected(wsContext);

        c.setHandled(true);

        FullHttpRequest retainedReq = nettyReq.retain();
        final String upgradePath = path;
        // One serialized virtual thread per connection: the registration callback runs first, then
        // the pump drains inbound messages on that same thread. User code — including blocking
        // handlers — never executes on the event loop; a slow handler stalls only its own
        // connection (reads are suspended, TCP backpressure), never other connections.
        Thread.startVirtualThread(
                () -> {
                    try {
                        wsMatch.handler().handle(wsContext);
                    } catch (Exception e) {
                        log.error(
                                "WebSocket handler registration failed for path={}",
                                upgradePath,
                                e);
                        pump.registrationFailed();
                        io.netty.util.ReferenceCountUtil.safeRelease(retainedReq);
                        return;
                    }
                    try {
                        nettyCtx.executor()
                                .execute(
                                        () -> {
                                            if (!nettyCtx.channel().isActive()) {
                                                io.netty.util.ReferenceCountUtil.safeRelease(
                                                        retainedReq);
                                                pump.connectionClosed();
                                                return;
                                            }
                                            try {
                                                // Logs upgrade failures (e.g. an invalid handshake
                                                // that Netty rejects
                                                // with a 500 of its own) on the server side.
                                                // Nothing
                                                // is sent to the
                                                // client beyond Netty's standard response — this is
                                                // purely diagnostic
                                                // so a red WS IT can be triaged from the server
                                                // log,
                                                // not guessed
                                                // at.
                                                nettyCtx.pipeline()
                                                        .addLast(
                                                                new io.netty.channel
                                                                        .ChannelInboundHandlerAdapter() {
                                                                    @Override
                                                                    public void exceptionCaught(
                                                                            ChannelHandlerContext
                                                                                    ctx,
                                                                            Throwable cause) {
                                                                        log.error(
                                                                                "WebSocket upgrade"
                                                                                    + " failed for"
                                                                                    + " path={}",
                                                                                upgradePath,
                                                                                cause);
                                                                        ctx.close();
                                                                    }
                                                                });
                                                nettyCtx.pipeline()
                                                        .addLast(
                                                                new io.netty.handler.codec.http
                                                                        .websocketx
                                                                        .WebSocketServerProtocolHandler(
                                                                        uri,
                                                                        true,
                                                                        config
                                                                                .maxWebSocketFrameSize()));
                                                if (config.wsHeartbeatInterval() > 0) {
                                                    // Post-upgrade the connection has no
                                                    // read-timeout
                                                    // protection and legitimately stays quiet —
                                                    // liveness
                                                    // is owned by protocol-level ping/pong from
                                                    // here on.
                                                    nettyCtx.pipeline()
                                                            .addLast(
                                                                    new WebSocketHeartbeatHandler(
                                                                            config
                                                                                    .wsHeartbeatInterval()));
                                                }
                                                // The broadcaster writes fire-and-forget: this
                                                // watchdog, not the broadcaster, polices members
                                                // whose outbound side stalls.
                                                OutboundStallHandler.attach(
                                                        nettyCtx, config.outboundSendTimeout());
                                                nettyCtx.pipeline()
                                                        .addLast(
                                                                new SummerWebSocketFrameHandler(
                                                                        wsContext, pump));
                                                nettyCtx.fireChannelRead(retainedReq);
                                            } catch (Throwable t) {
                                                io.netty.util.ReferenceCountUtil.safeRelease(
                                                        retainedReq);
                                                log.error(
                                                        "WebSocket pipeline setup failed for"
                                                                + " path={}",
                                                        upgradePath,
                                                        t);
                                                pump.connectionClosed();
                                                nettyCtx.close();
                                            }
                                        });
                    } catch (Exception e) {
                        // The event loop rejected the task (shutting down): no frame handler will
                        // ever be installed, so finish the (empty) queue instead of parking
                        // forever.
                        log.error("WebSocket pipeline setup failed for path={}", upgradePath, e);
                        io.netty.util.ReferenceCountUtil.safeRelease(retainedReq);
                        pump.connectionClosed();
                        nettyCtx.close();
                    }
                    pump.drain();
                });
    }
}
