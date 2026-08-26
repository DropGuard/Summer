package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.BodyConverter;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.ResponseSink;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NettyResponseSink implements ResponseSink {

    private static final Logger log = LoggerFactory.getLogger(NettyResponseSink.class);

    private final ChannelHandlerContext ctx;
    private final boolean keepAlive;

    public NettyResponseSink(ChannelHandlerContext ctx, boolean keepAlive) {
        this.ctx = ctx;
        this.keepAlive = keepAlive;
    }

    @Override
    public void sendBytes(HttpStatus status, Map<String, String> headers, byte[] body) {
        ByteBuf buf =
                (body != null && body.length > 0)
                        ? Unpooled.wrappedBuffer(body)
                        : Unpooled.EMPTY_BUFFER;
        writeResponse(status, headers, buf);
    }

    @Override
    public void sendObject(
            HttpStatus status,
            Map<String, String> headers,
            Object resultObject,
            BodyConverter converter) {
        byte[] bytes;
        try {
            bytes = converter.write(resultObject);
        } catch (Exception e) {
            log.error("Serialization error", e);
            sendErrorResponse();
            return;
        }
        writeResponse(status, headers, Unpooled.wrappedBuffer(bytes));
    }

    @Override
    public void sendEmpty(HttpStatus status, Map<String, String> headers) {
        writeResponse(status, headers, Unpooled.EMPTY_BUFFER);
    }

    private void writeResponse(HttpStatus status, Map<String, String> headers, ByteBuf content) {
        FullHttpResponse resp =
                new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(status.code()), content);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            resp.headers().set(entry.getKey(), entry.getValue());
        }

        if (keepAlive) {
            if (!resp.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            }
            resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            // Buffered response fully written: the channel is back to a keep-alive
            // gap, where read-idle detection applies again.
            ctx.writeAndFlush(resp).addListener(f -> ChannelInflight.markComplete(ctx));
        } else {
            resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void sendErrorResponse() {
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
    }
}
