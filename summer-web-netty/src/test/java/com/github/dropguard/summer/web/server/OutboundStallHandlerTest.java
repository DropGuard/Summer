package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class OutboundStallHandlerTest {

    private static void tick(EmbeddedChannel channel, long ms) {
        channel.advanceTimeBy(ms, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
    }

    /** Flips the stuck channel to unwritable: 64 pending bytes vs a watermark of 8. */
    private static void stuffBuffer(EmbeddedChannel channel) {
        channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[64]));
        assertFalse(channel.isWritable(), "the stuck channel must be unwritable after a write");
    }

    @Test
    void watchdogClosesChannelStalledBeyondBudget() {
        EmbeddedChannel channel = NettyTestSupport.newStuckChannel(new ChannelHandler[0]);
        OutboundStallHandler.attach(channel.pipeline().firstContext(), 100);
        OutboundStallHandler.attach(channel.pipeline().firstContext(), 100);
        long installed =
                channel.pipeline().names().stream()
                        .filter(OutboundStallHandler.NAME::equals)
                        .count();
        assertEquals(1, installed, "attaching twice must not install a second watchdog");

        stuffBuffer(channel);
        tick(channel, 99);
        assertTrue(channel.isOpen(), "inside the budget the connection must survive");
        tick(channel, 1);
        assertFalse(channel.isOpen(), "stalled past the budget the connection must be closed");
    }

    @Test
    void watchdogCancelsWhenTheChannelRecovers() {
        AtomicBoolean draining = new AtomicBoolean(false);
        EmbeddedChannel channel = NettyTestSupport.newStuckChannel(draining, new ChannelHandler[0]);
        OutboundStallHandler.attach(channel.pipeline().firstContext(), 100);

        stuffBuffer(channel);
        draining.set(true);
        channel.flush();
        assertTrue(channel.isWritable(), "drained buffer must restore writability");

        tick(channel, 500);
        assertTrue(channel.isOpen(), "a connection that recovered must not be closed");
    }

    @Test
    void sendGateBlocksForTheBudgetThenCloses() {
        EmbeddedChannel channel = NettyTestSupport.newStuckChannel(new ChannelHandler[0]);
        NettyWebSocketContext ctx =
                new NettyWebSocketContext(
                        channel.pipeline().firstContext(),
                        java.util.Map.of(),
                        java.util.Map.of(),
                        java.util.List.of(),
                        null,
                        50);

        long start = System.nanoTime();
        ctx.send("hello");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(
                elapsedMs >= 50, "send must block for the stall budget, was " + elapsedMs + "ms");
        assertTrue(elapsedMs < 5000, "send must return at the budget, not hang");
        assertFalse(channel.isOpen(), "timed-out send must close the connection");
    }

    @Test
    void watchdogCompletesPendingWritesOnClose() {
        EmbeddedChannel channel = NettyTestSupport.newStuckChannel(new ChannelHandler[0]);
        OutboundStallHandler.attach(channel.pipeline().firstContext(), 100);

        // A writer's frame pinned the buffer; its promise pends exactly like a blocked
        // virtual-thread writer's does. Driven on the test thread — EmbeddedChannel is
        // single-threaded by design, and a real second thread here is a data race.
        ChannelFuture write =
                channel.writeAndFlush(
                        Unpooled.wrappedBuffer(new byte[64]),
                        new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE));
        assertFalse(channel.isWritable());
        assertFalse(write.isDone());

        // Overshoot the budget: the embedded scheduling clock has sub-millisecond rounding, so
        // firing exactly at the boundary is not deterministic.
        tick(channel, 300);
        assertFalse(channel.isOpen(), "stalled past the budget the connection must close");
        assertTrue(write.isDone(), "the close must release pending writers");
        assertFalse(write.isSuccess(), "a watchdog-closed write must fail, not succeed");
    }
}
