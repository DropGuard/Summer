package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WebSocketInboundPumpTest {

    private static NettyWebSocketContext context(EmbeddedChannel channel) {
        // NettyTestSupport.newChannel() installs a tail handler, so firstContext() is usable.
        return new NettyWebSocketContext(
                channel.pipeline().firstContext(), Map.of(), Map.of(), List.of(), null, 0);
    }

    @Test
    void deliversBufferedMessagesInOrderThenClose() throws Exception {
        EmbeddedChannel channel = NettyTestSupport.newChannel();
        NettyWebSocketContext ctx = context(channel);
        List<String> delivered = new ArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        ctx.onMessage(delivered::add);
        ctx.onClose(closed::countDown);

        WebSocketInboundPump pump = new WebSocketInboundPump(ctx, channel, 8);
        pump.accept("m1");
        pump.accept("m2");
        pump.connectionClosed();
        assertTrue(delivered.isEmpty(), "nothing is delivered until the pump drains");

        Thread worker = Thread.ofVirtual().start(pump::drain);
        assertTrue(
                closed.await(5, TimeUnit.SECONDS), "close must fire after the buffered messages");
        worker.join(1000);
        assertFalse(worker.isAlive(), "the drain loop must terminate on the close event");
        assertEquals(List.of("m1", "m2"), delivered);
    }

    @Test
    void suspendsReadsAtHighWatermarkAndResumesAtLow() throws Exception {
        EmbeddedChannel channel = NettyTestSupport.newChannel();
        NettyWebSocketContext ctx = context(channel);
        // Capacity 4: high watermark 3, low watermark 2.
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        ctx.onMessage(
                msg -> {
                    if (msg.equals("m1")) {
                        first.countDown();
                    } else if (msg.equals("m2")) {
                        second.countDown();
                    }
                });
        ctx.onClose(closed::countDown);

        WebSocketInboundPump pump = new WebSocketInboundPump(ctx, channel, 4);
        pump.accept("m1");
        pump.accept("m2");
        assertTrue(channel.config().isAutoRead(), "reads stay on below the high watermark");
        pump.accept("m3");
        assertFalse(
                channel.config().isAutoRead(),
                "reaching the high watermark suspends inbound reads");

        pump.connectionClosed();
        Thread worker = Thread.ofVirtual().start(pump::drain);
        // m2 being taken proves m1's post-processing resume already ran (latch happens-before).
        assertTrue(second.await(5, TimeUnit.SECONDS));
        assertTrue(
                channel.config().isAutoRead(),
                "draining back to the low watermark resumes inbound reads");
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        worker.join(1000);
    }

    @Test
    void burstBeyondCapacityIsAbsorbedInsteadOfClosing() throws Exception {
        EmbeddedChannel channel = NettyTestSupport.newChannel();
        NettyWebSocketContext ctx = context(channel);
        List<String> delivered = new ArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        ctx.onMessage(delivered::add);
        ctx.onClose(closed::countDown);

        // Capacity 2 -> high watermark 1: reads suspend after the first message, yet frames
        // decoded within the same read batch keep arriving. They must be buffered and delivered
        // in order — a well-behaved backpressured peer must not be punished for batch arrivals.
        WebSocketInboundPump pump = new WebSocketInboundPump(ctx, channel, 2);
        pump.accept("m1");
        assertFalse(channel.config().isAutoRead(), "reads suspend at the high watermark");
        pump.accept("m2");
        pump.accept("m3");
        pump.accept("m4");
        assertTrue(channel.isOpen(), "batch overrun must not close the connection");

        pump.connectionClosed();
        Thread worker = Thread.ofVirtual().start(pump::drain);
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        worker.join(1000);
        assertEquals(List.of("m1", "m2", "m3", "m4"), delivered);
    }

    @Test
    void handlerFailureClosesConnectionAndStillFiresClose() throws Exception {
        EmbeddedChannel channel = NettyTestSupport.newChannel();
        NettyWebSocketContext ctx = context(channel);
        List<String> attempted = new ArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        ctx.onMessage(
                msg -> {
                    attempted.add(msg);
                    throw new IllegalStateException("handler boom");
                });
        ctx.onClose(closed::countDown);

        WebSocketInboundPump pump = new WebSocketInboundPump(ctx, channel, 8);
        pump.accept("m1");
        pump.accept("m2");
        pump.connectionClosed();

        Thread worker = Thread.ofVirtual().start(pump::drain);
        assertTrue(closed.await(5, TimeUnit.SECONDS), "onClose must fire even after a failure");
        worker.join(1000);
        assertEquals(List.of("m1"), attempted, "processing stops at the failing message");
        assertFalse(channel.isActive(), "a failing handler must close the connection");
    }

    @Test
    void registrationFailureFiresCloseImmediately() {
        EmbeddedChannel channel = NettyTestSupport.newChannel();
        NettyWebSocketContext ctx = context(channel);
        CountDownLatch closed = new CountDownLatch(1);
        ctx.onClose(closed::countDown);

        WebSocketInboundPump pump = new WebSocketInboundPump(ctx, channel, 8);
        pump.registrationFailed();

        assertEquals(0, closed.getCount(), "close handler must fire on registration failure");
        assertFalse(channel.isActive(), "a failed registration must close the connection");
        assertDoesNotThrow(pump::connectionClosed, "late close events must be a no-op");
        assertDoesNotThrow(pump::drain, "drain after a finished consumer must return immediately");
    }
}
