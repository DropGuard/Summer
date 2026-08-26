package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WebSocketHeartbeatHandlerTest {

    /**
     * Deterministic clock for the handler's elapsed-time decisions. The embedded loop keeps its own
     * clock for task scheduling — both must advance together per simulated tick.
     */
    private static final class FakeClock extends AtomicLong {
        void advanceMillis(long ms) {
            addAndGet(TimeUnit.MILLISECONDS.toNanos(ms));
        }
    }

    private static void tick(EmbeddedChannel channel, FakeClock clock, long ms) {
        channel.advanceTimeBy(ms, TimeUnit.MILLISECONDS);
        clock.advanceMillis(ms);
        channel.runScheduledPendingTasks();
    }

    @Test
    void sendsPingsAndStaysOpenWhilePongsArrive() {
        FakeClock clock = new FakeClock();
        EmbeddedChannel channel =
                NettyTestSupport.newChannel(new WebSocketHeartbeatHandler(50, clock::get));

        // First tick (interval reached): healthy connection -> ping goes out.
        tick(channel, clock, 50);
        assertInstanceOf(PingWebSocketFrame.class, channel.readOutbound());

        // Client answers at t=50: liveness clock resets to that point.
        channel.writeInbound(new PongWebSocketFrame());

        // t=120: 70ms since the pong — inside the 2x grace window -> another ping.
        tick(channel, clock, 70);
        assertTrue(channel.isOpen(), "fresh pong must keep the connection open");
        assertInstanceOf(PingWebSocketFrame.class, channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void closesConnectionWhenNoPongArrives() {
        FakeClock clock = new FakeClock();
        EmbeddedChannel channel =
                NettyTestSupport.newChannel(new WebSocketHeartbeatHandler(50, clock::get));

        // First tick: ping out, connection still within grace.
        tick(channel, clock, 50);
        assertInstanceOf(PingWebSocketFrame.class, channel.readOutbound());

        // Silence for longer than two full intervals: dead peer -> close.
        tick(channel, clock, 250);
        assertFalse(channel.isOpen(), "missing pong must close the connection");
    }

    @Test
    void handlerSurvivesImmediateClose() {
        FakeClock clock = new FakeClock();
        EmbeddedChannel channel =
                NettyTestSupport.newChannel(new WebSocketHeartbeatHandler(1, clock::get));
        channel.finish();
        assertFalse(channel.isOpen());
    }
}
