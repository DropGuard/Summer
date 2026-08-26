package com.github.dropguard.summer.web.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.util.AttributeKey;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-initiated WebSocket heartbeat using RFC 6455 ping/pong control frames — protocol-level
 * infrastructure, invisible to user message handlers. Browsers answer pongs automatically and
 * {@code WebSocketServerProtocolHandler} answers client pings, so this detects dead peers on both
 * sides without any client cooperation.
 *
 * <p>A ping is sent every {@code intervalMs}. The connection is closed when no pong has arrived for
 * twice that long (one full cycle of grace). Read-idle detection cannot do this job: a quiet but
 * healthy WebSocket produces no inbound traffic for arbitrarily long periods.
 */
final class WebSocketHeartbeatHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHeartbeatHandler.class);

    private static final AttributeKey<Long> LAST_PONG =
            AttributeKey.valueOf("summer.wsLastPongNanos");

    private final long intervalMs;
    private final LongSupplier clock;
    private ScheduledFuture<?> task;

    WebSocketHeartbeatHandler(long intervalMs) {
        this(intervalMs, System::nanoTime);
    }

    /** Test-visible clock injection so elapsed-time behaviour can be driven deterministically. */
    WebSocketHeartbeatHandler(long intervalMs, LongSupplier clock) {
        this.intervalMs = intervalMs;
        this.clock = clock;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.channel().attr(LAST_PONG).set(clock.getAsLong());
        task =
                ctx.executor()
                        .scheduleAtFixedRate(
                                () -> checkAndPing(ctx.channel()),
                                intervalMs,
                                intervalMs,
                                TimeUnit.MILLISECONDS);
    }

    private void checkAndPing(Channel channel) {
        if (!channel.isActive()) {
            cancel(channel);
            return;
        }
        Long last = channel.attr(LAST_PONG).get();
        long elapsedMs =
                TimeUnit.NANOSECONDS.toMillis(clock.getAsLong() - (last == null ? 0 : last));
        if (elapsedMs > 2 * intervalMs) {
            log.debug(
                    "[Summer] Closing WebSocket: no pong for {} ms ({})",
                    elapsedMs,
                    channel.remoteAddress());
            channel.close();
            return;
        }
        channel.writeAndFlush(new PingWebSocketFrame())
                .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof PongWebSocketFrame) {
            ctx.channel().attr(LAST_PONG).set(System.nanoTime());
        }
        // Control frames are transport concerns here; everything continues down
        // the pipeline untouched.
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancel(ctx.channel());
        ctx.fireChannelInactive();
    }

    private void cancel(Channel channel) {
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }
        channel.attr(LAST_PONG).set(null);
    }
}
