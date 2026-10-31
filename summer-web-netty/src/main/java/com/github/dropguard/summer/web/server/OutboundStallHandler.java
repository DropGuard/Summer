package com.github.dropguard.summer.web.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Outbound flow-control enforcement shared by WebSocket sends and chunked/SSE streams, in two
 * halves that share one budget ({@code server.outbound-send-timeout}):
 *
 * <p>The write gate ({@link #awaitOrClose}) blocks the calling virtual thread until the write has
 * actually drained into the socket. This is the "write completion is the backpressure signal"
 * semantic of async stacks (Vert.x/Quarkus), expressed blocking so a fire-and-forget producer
 * cannot bypass it: in a synchronous API the signal and the enforcement are the same call.
 *
 * <p>The watchdog ({@code channelWritabilityChanged}) closes a connection whose outbound side has
 * been continuously unwritable for the budget — a peer that stopped reading (zero TCP window) would
 * otherwise pin the buffer and any blocked writer forever, and TCP's own retransmission timeout
 * only gives up after minutes. The watchdog is autonomous per connection, so fire-and-forget
 * producers (broadcaster fan-out) are policed without the broadcaster ever waiting on its slowest
 * member, and closing the channel is what releases writers parked in {@link #awaitOrClose}: it
 * fails their pending write futures.
 *
 * <p>Ordinary request/response writes are bounded by their own size (the handler materialises the
 * response first) and are deliberately not policed, so the watchdog is attached only to WebSocket
 * connections and chunked/SSE streams.
 */
final class OutboundStallHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(OutboundStallHandler.class);

    static final String NAME = "summer-outbound-stall";

    private final long timeoutMs;
    private ScheduledFuture<?> stallTask;

    private OutboundStallHandler(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /** Attaches the watchdog to the connection unless one is already installed or disabled. */
    static void attach(ChannelHandlerContext ctx, long timeoutMs) {
        if (timeoutMs <= 0 || ctx.pipeline().get(NAME) != null) {
            return;
        }
        ctx.pipeline().addLast(NAME, new OutboundStallHandler(timeoutMs));
    }

    /** Removes the watchdog when a chunked stream ends but the keep-alive connection lives on. */
    static void detach(Channel channel) {
        if (channel.pipeline().get(NAME) != null) {
            channel.pipeline().remove(NAME);
        }
    }

    /**
     * Writes {@code msg} and blocks the calling thread until the write has drained into the socket,
     * or the stall budget expires (the connection is closed, which fails the write).
     *
     * <p>The write promise is bound to {@link GlobalEventExecutor} rather than the channel's event
     * loop: Netty's promise deadlock detector refuses to await a promise whose executor claims the
     * awaiting thread, and a channel's loop is exactly such a claimant for virtual threads doing
     * the flush on its behalf. The global executor never runs anything here (no listeners are
     * registered); it only makes the promise awaitable from application threads. The gated APIs
     * ({@code send}, chunked writes) are dispatched on virtual threads by the framework; even a
     * stray event-loop caller is only blocked for the budget before the close completes its own
     * future.
     */
    static void writeAndAwait(Channel channel, Object msg, long timeoutMs) {
        ChannelFuture write =
                channel.writeAndFlush(
                        msg, new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE));
        if (timeoutMs <= 0 || write.awaitUninterruptibly(timeoutMs)) {
            return;
        }
        log.warn(
                "[Summer] outbound write stalled over {} ms; closing {}",
                timeoutMs,
                channel.remoteAddress());
        channel.close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            cancelStallTask();
        } else if (stallTask == null) {
            stallTask =
                    ctx.executor()
                            .schedule(() -> closeStalled(ctx), timeoutMs, TimeUnit.MILLISECONDS);
        }
        ctx.fireChannelWritabilityChanged();
    }

    private void closeStalled(ChannelHandlerContext ctx) {
        stallTask = null;
        log.warn(
                "[Summer] outbound side unwritable for {} ms; closing stalled connection {}",
                timeoutMs,
                ctx.channel().remoteAddress());
        ctx.close();
    }

    private void cancelStallTask() {
        if (stallTask != null) {
            stallTask.cancel(false);
            stallTask = null;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancelStallTask();
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancelStallTask();
    }
}
