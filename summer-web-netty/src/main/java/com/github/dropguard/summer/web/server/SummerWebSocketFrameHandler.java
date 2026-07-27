package com.github.dropguard.summer.web.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SummerWebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(SummerWebSocketFrameHandler.class);

    private final NettyWebSocketContext wsContext;
    private final int maxFrameSize;

    public SummerWebSocketFrameHandler(NettyWebSocketContext wsContext, int maxFrameSize) {
        this.wsContext = wsContext;
        this.maxFrameSize = maxFrameSize;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        int frameBytes = frame.content().readableBytes();
        if (frameBytes > maxFrameSize) {
            log.warn("WebSocket frame too large: {} bytes (max: {})", frameBytes, maxFrameSize);
            try {
                ctx.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return;
        }
        String text = frame.text();
        wsContext.invokeMessageConsumer(text);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        wsContext.invokeCloseHandler();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket error", cause);
        try {
            ctx.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
