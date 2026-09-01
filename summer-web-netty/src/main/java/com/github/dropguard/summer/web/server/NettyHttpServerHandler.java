package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Middleware;
import com.github.dropguard.summer.web.Request;
import com.github.dropguard.summer.web.ServerConfig;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NettyHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(NettyHttpServerHandler.class);

    private final NettyHttpServer server;
    private final ServerConfig config;
    private final WebServerDependencies deps;

    /** The fully middleware-wrapped dispatch chain, built once at construction. */
    private final Handler handlerChain;

    public NettyHttpServerHandler(
            NettyHttpServer server, ServerConfig config, WebServerDependencies deps) {
        this.server = server;
        this.config = config;
        this.deps = deps;
        this.handlerChain = buildHandlerChain();
    }

    /**
     * Per-request slot for the raw Netty artifacts, read by the cached {@link #handlerChain}. Each
     * request runs on a fresh virtual thread (no pooling) and the chain is synchronous, so the
     * ScopedValue is inherently safe; bound dynamically in {@link #processRequest}.
     */
    private static final ScopedValue<RequestSlot> REQUEST_SLOT = ScopedValue.newInstance();

    private record RequestSlot(ChannelHandlerContext ctx, FullHttpRequest nettyReq) {}

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest nettyReq) {
        if (server != null) server.getActiveConnections().incrementAndGet();

        // The request is fully received (this handler sits behind the aggregator):
        // slow-request protection has done its job for this connection and must not
        // fire again — server-push responses legitimately have no inbound traffic.
        removeReadTimeout(ctx);
        ChannelInflight.markActive(ctx);

        // 1. Check HTTP Keep-Alive
        boolean keepAlive = HttpUtil.isKeepAlive(nettyReq);

        // 2. Retain the request because we are handing it off to another thread
        nettyReq.retain();

        // 3. Dispatch to Virtual Thread (Loom)
        Thread.startVirtualThread(
                () -> {
                    try {
                        processRequest(ctx, nettyReq, keepAlive);
                    } finally {
                        // Must release the retained Netty buffer once done processing
                        nettyReq.release();
                        if (server != null) server.getActiveConnections().decrementAndGet();
                    }
                });
    }

    private static void removeReadTimeout(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(ReadTimeoutHandler.class) != null) {
            ctx.pipeline().remove(ReadTimeoutHandler.class);
        }
    }

    private void sendSimpleResponse(
            ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        byte[] bytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FullHttpResponse response =
                new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        ctx.writeAndFlush(response)
                .addListener(
                        f -> {
                            ChannelInflight.markComplete(ctx);
                            ctx.close();
                        });
    }

    private void processRequest(
            ChannelHandlerContext ctx, FullHttpRequest nettyReq, boolean keepAlive) {
        try {
            Request request = NettyRequestAdapter.adapt(nettyReq);
            request.setLazyAttribute(
                    com.github.dropguard.summer.web.RequestAttributes.CHUNKED_RESPONSE,
                    () -> new NettyChunkedResponse(ctx, keepAlive));
            request.setLazyAttribute(
                    com.github.dropguard.summer.web.RequestAttributes.SSE_STREAM,
                    () ->
                            new NettySseStream(
                                    request.getAttribute(
                                            com.github.dropguard.summer.web.RequestAttributes
                                                    .CHUNKED_RESPONSE)));
            HttpContext webCtx = new HttpContext(request, deps.jsonConverter());

            if (request.getMethod() == HttpMethod.UNKNOWN) {
                webCtx.text(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed");
                webCtx.flushTo(new NettyResponseSink(ctx, keepAlive));
                return;
            }

            ScopedValue.where(REQUEST_SLOT, new RequestSlot(ctx, nettyReq))
                    .call(
                            () -> {
                                try {
                                    handlerChain.handle(webCtx);
                                } catch (Exception e) {
                                    handleException(webCtx, e);
                                }
                                return null;
                            });

            webCtx.flushTo(new NettyResponseSink(ctx, keepAlive));

        } catch (Exception e) {
            log.error("Fatal framework error", e);
            sendErrorResponse(ctx, keepAlive);
        }
    }

    private Handler buildHandlerChain() {
        Handler handler = this::dispatch;
        for (Middleware middleware : deps.middlewares()) {
            handler = middleware.apply(handler);
        }
        return handler;
    }

    private void dispatch(HttpContext c) throws Exception {
        RequestSlot slot = REQUEST_SLOT.get();
        if (deps.wsUpgradeHandler().isWebSocketUpgrade(slot.nettyReq)) {
            deps.wsUpgradeHandler().handleUpgrade(slot.ctx, slot.nettyReq, c);
            return;
        }

        deps.httpRouter().route(c);
    }

    private void handleException(HttpContext c, Exception e) {
        if (deps.exceptionRegistry() != null) {
            Handler customHandler = deps.exceptionRegistry().getHandler(e);
            if (customHandler != null) {
                try {
                    c.request()
                            .setAttribute(
                                    com.github.dropguard.summer.web.RequestAttributes
                                            .LAST_EXCEPTION,
                                    e);
                    customHandler.handle(c);
                    return;
                } catch (Exception handlerException) {
                    log.warn(
                            "Exception handler failed for: {}",
                            e.getClass().getName(),
                            handlerException);
                }
            }
        }

        if (e instanceof com.github.dropguard.summer.web.exception.HttpException httpEx) {
            com.github.dropguard.summer.web.HttpStatus status =
                    com.github.dropguard.summer.web.HttpStatus.fromCode(httpEx.getStatus());
            c.status(status);
            if (httpEx.getMessage() != null) {
                c.text(status, httpEx.getMessage());
            }
            return;
        }

        if (e instanceof com.github.dropguard.summer.web.exception.SummerWebException webEx) {
            com.github.dropguard.summer.web.HttpStatus status = webEx.statusCode();
            c.status(status);
            if (webEx.getMessage() != null) {
                c.text(status, webEx.getMessage());
            }
            return;
        }

        c.error(e);
        log.error("Request failed: {}", c.request().getPath(), e);
    }

    private FullHttpResponse sendErrorResponse(ChannelHandlerContext ctx, boolean keepAlive) {
        byte[] errorBytes =
                "Internal Server Error".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FullHttpResponse errorResp =
                new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        Unpooled.wrappedBuffer(errorBytes));
        errorResp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        errorResp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, errorBytes.length);
        if (keepAlive) {
            errorResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(errorResp).addListener(f -> ChannelInflight.markComplete(ctx));
        } else {
            errorResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(errorResp).addListener(ChannelFutureListener.CLOSE);
        }
        return errorResp;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            // Close only genuinely idle keep-alive gaps. Channels with response work
            // in flight (open SSE/chunked streams, an in-flight handler, or a
            // WebSocket session) legitimately stay quiet on the read side.
            if (!ChannelInflight.isActive(ctx.channel())) {
                log.debug("[Summer] Closing idle connection: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception", cause);
        try {
            ctx.close();
        } catch (Exception closeFailure) {
            // Best-effort cleanup: a close failure must not turn into a throwing callback,
            // which would re-enter Netty's exception path from within exceptionCaught.
            log.warn("Failed to close channel after exception", closeFailure);
        }
    }
}
