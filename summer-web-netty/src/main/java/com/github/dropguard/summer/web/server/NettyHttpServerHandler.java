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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NettyHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(NettyHttpServerHandler.class);

    private final NettyHttpServer server;
    private final ServerConfig config;
    private final WebServerDependencies deps;

    public NettyHttpServerHandler(
            NettyHttpServer server, ServerConfig config, WebServerDependencies deps) {
        this.server = server;
        this.config = config;
        this.deps = deps;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest nettyReq) {
        if (server != null) server.getActiveConnections().incrementAndGet();

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

    private void sendSimpleResponse(
            ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        byte[] bytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FullHttpResponse response =
                new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void processRequest(
            ChannelHandlerContext ctx, FullHttpRequest nettyReq, boolean keepAlive) {
        try {
            Request request = NettyRequestAdapter.adapt(nettyReq);
            HttpContext webCtx = new HttpContext(request, deps.jsonConverter());

            if (request.getMethod() == HttpMethod.UNKNOWN) {
                webCtx.text(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed");
                sendResponse(ctx, webCtx, keepAlive);
                return;
            }

            Handler handler = createHandlerChain(ctx, nettyReq);
            try {
                handler.handle(webCtx);
            } finally {
                // Tear down the request-scoped context. Runs on a fresh virtual thread
                // per request (no pooling), so this fully releases the binding.
                com.github.dropguard.summer.web.RequestContextHolder.clear();
            }

            if (webCtx.isHandled()) {
                return;
            }

            if (webCtx.status() == null) {
                webCtx.text(HttpStatus.NOT_FOUND, "Not Found");
            }

            sendResponse(ctx, webCtx, keepAlive);

        } catch (Exception e) {
            log.error("Fatal framework error", e);
            sendErrorResponse(ctx, keepAlive);
        }
    }

    private Handler createHandlerChain(ChannelHandlerContext nettyCtx, FullHttpRequest nettyReq) {
        Handler dispatchHandler =
                (c) -> {
                    try {
                        if (deps.wsUpgradeHandler().isWebSocketUpgrade(nettyReq)) {
                            deps.wsUpgradeHandler().handleUpgrade(nettyCtx, nettyReq, c);
                            return;
                        }

                        deps.httpRouter().route(c);
                    } catch (Exception e) {
                        if (deps.exceptionRegistry() != null) {
                            Handler customHandler = deps.exceptionRegistry().getHandler(e);
                            if (customHandler != null) {
                                try {
                                    c.request()
                                            .setAttribute(
                                                    com.github.dropguard.summer.web
                                                            .RequestAttributes.LAST_EXCEPTION,
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

                        if (e
                                instanceof
                                com.github.dropguard.summer.web.exception.SummerWebException
                                        webEx) {
                            c.status(webEx.statusCode());
                            if (webEx.getMessage() != null) {
                                c.text(webEx.statusCode(), webEx.getMessage());
                            }
                            return;
                        }

                        c.error(e);
                        log.error("Request failed: {}", c.request().getPath(), e);
                    }
                };

        Handler handler = dispatchHandler;
        for (Middleware middleware : deps.middlewares()) {
            handler = middleware.apply(handler);
        }

        return handler;
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpContext webCtx, boolean keepAlive) {
        HttpResponseStatus status = HttpResponseStatus.valueOf(webCtx.status().code());

        FullHttpResponse nettyResp;
        if (webCtx.resultObject() != null && webCtx.converter() != null) {
            nettyResp = serializeResponse(ctx, webCtx, status, keepAlive);
            if (nettyResp == null) {
                return; // Error response already sent
            }
        } else if (webCtx.body() != null && webCtx.body().length > 0) {
            nettyResp =
                    new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(webCtx.body()));
        } else {
            nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        }

        // Apply headers
        for (Map.Entry<String, String> entry : webCtx.headers().entrySet()) {
            nettyResp.headers().set(entry.getKey(), entry.getValue());
        }

        if (keepAlive) {
            if (!nettyResp.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                nettyResp
                        .headers()
                        .setInt(
                                HttpHeaderNames.CONTENT_LENGTH,
                                nettyResp.content().readableBytes());
            }
            nettyResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(nettyResp);
        } else {
            nettyResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(nettyResp).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private FullHttpResponse serializeResponse(
            ChannelHandlerContext ctx,
            HttpContext webCtx,
            HttpResponseStatus status,
            boolean keepAlive) {
        io.netty.buffer.ByteBuf buf = ctx.alloc().directBuffer();
        try (io.netty.buffer.ByteBufOutputStream out =
                new io.netty.buffer.ByteBufOutputStream(buf)) {
            webCtx.converter().writeToStream(webCtx.resultObject(), out);
        } catch (Exception e) {
            buf.release();
            log.error("Serialization error", e);
            sendErrorResponse(ctx, keepAlive);
            return null;
        }
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
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
            ctx.writeAndFlush(errorResp);
        } else {
            errorResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(errorResp).addListener(ChannelFutureListener.CLOSE);
        }
        return errorResp;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.debug("[Summer] Closing idle connection: {}", ctx.channel().remoteAddress());
            ctx.close();
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
