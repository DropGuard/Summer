package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.ChunkedResponse;
import com.github.dropguard.summer.web.HttpStatus;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Netty-backed implementation of {@link ChunkedResponse}. */
class NettyChunkedResponse implements ChunkedResponse {

    private static final Logger log = LoggerFactory.getLogger(NettyChunkedResponse.class);

    private final ChannelHandlerContext ctx;
    private final boolean keepAlive;
    private final HttpHeaders customHeaders = new DefaultHttpHeaders();
    private final AtomicBoolean headerSent = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    // Server-level counter of open streams (null in tests): lets shutdown drain see streams
    // whose handler has already returned — invisible to the active-connection count.
    private final java.util.concurrent.atomic.AtomicInteger streamCounter;
    // Guards the exactly-once release of the streamCounter slot across every terminal path —
    // close() and the orphan sweep — so a path that skips close() cannot leak the slot.
    private final AtomicBoolean streamSlotReleased = new AtomicBoolean(false);
    private HttpResponseStatus httpStatus = HttpResponseStatus.OK;
    private String contentType = "application/octet-stream";

    public NettyChunkedResponse(ChannelHandlerContext ctx, boolean keepAlive) {
        this(ctx, keepAlive, null);
    }

    public NettyChunkedResponse(
            ChannelHandlerContext ctx,
            boolean keepAlive,
            java.util.concurrent.atomic.AtomicInteger streamCounter) {
        this.ctx = ctx;
        this.keepAlive = keepAlive;
        this.streamCounter = streamCounter;
        if (streamCounter != null) {
            streamCounter.incrementAndGet();
        }
    }

    @Override
    public ChunkedResponse header(String name, String value) {
        if (headerSent.get()) {
            throw new IllegalStateException(
                    "Cannot add headers after response streaming has started");
        }
        customHeaders.set(name, value);
        return this;
    }

    @Override
    public ChunkedResponse contentType(String contentType) {
        if (headerSent.get()) {
            throw new IllegalStateException(
                    "Cannot change content-type after response streaming has started");
        }
        this.contentType = contentType;
        return this;
    }

    @Override
    public ChunkedResponse status(HttpStatus status) {
        if (headerSent.get()) {
            throw new IllegalStateException(
                    "Cannot change status after response streaming has started");
        }
        this.httpStatus = HttpResponseStatus.valueOf(status.code());
        return this;
    }

    private void ensureHeaderSent() {
        if (headerSent.compareAndSet(false, true)) {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, httpStatus);
            response.headers().add(customHeaders);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            ctx.writeAndFlush(response);
        }
    }

    @Override
    public void write(byte[] data) {
        if (closed.get() || !ctx.channel().isActive()) {
            log.debug(
                    "Discarding chunked write (length={}): response closed or channel inactive",
                    data != null ? data.length : 0);
            return;
        }
        ensureHeaderSent();
        ctx.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(data)));
    }

    @Override
    public void write(String text) {
        if (text != null) {
            write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public void flush() {
        if (ctx.channel().isActive()) {
            ctx.flush();
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get() || !ctx.channel().isActive();
    }

    /**
     * Releases the drain-accounting slot exactly once, on every terminal path: {@link #close()} by
     * the handler, and the dispatch finally's orphan sweep even when the client already
     * disconnected — {@code isClosed()} is then already true, so {@code close()} would never run
     * and the slot used to leak, leaving every later graceful shutdown of this server instance to
     * drain for the full budget and misreport the leftover count.
     */
    void releaseStreamSlot() {
        if (streamCounter != null && streamSlotReleased.compareAndSet(false, true)) {
            streamCounter.decrementAndGet();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            releaseStreamSlot();
            ensureHeaderSent();
            var future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (!keepAlive) {
                future.addListener(ChannelFutureListener.CLOSE);
            } else {
                // Stream finished: back to a keep-alive gap where read-idle
                // detection applies again.
                future.addListener(f -> ChannelInflight.markComplete(ctx));
            }
        }
    }
}
