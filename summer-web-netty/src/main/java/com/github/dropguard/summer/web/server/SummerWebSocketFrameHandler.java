package com.github.dropguard.summer.web.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SummerWebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(SummerWebSocketFrameHandler.class);

    private final NettyWebSocketContext wsContext;
    private final WebSocketInboundPump pump;

    public SummerWebSocketFrameHandler(NettyWebSocketContext wsContext, WebSocketInboundPump pump) {
        this.wsContext = wsContext;
        this.pump = pump;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        // Oversized frames never reach this handler: the frame decoder enforces
        // maxWebSocketFrameSize during decode (TooLongFrameException -> exceptionCaught).
        // The event loop only buffers the frame; the connection's virtual thread delivers it.
        pump.accept(frame.text());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Queue the close event instead of invoking the handler here: it must fire on the
        // connection's virtual thread, after every message buffered before the disconnect.
        pump.connectionClosed();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket error", cause);
        ctx.close();
    }
}
