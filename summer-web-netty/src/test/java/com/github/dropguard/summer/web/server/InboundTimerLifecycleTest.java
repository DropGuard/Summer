package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dropguard.summer.web.BodyConverter;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.HttpStatus;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/**
 * Pins the inbound-timer lifecycle: read-timeout protection covers only request reception, and
 * read-idle detection must not kill channels with response work in flight.
 */
class InboundTimerLifecycleTest {

    private NettyHttpServerHandler newHandler(HttpRouter router) {
        WebServerDependencies deps = mock(WebServerDependencies.class);
        when(deps.middlewares()).thenReturn(List.of());
        when(deps.jsonConverter()).thenReturn(mock(BodyConverter.class));
        when(deps.wsUpgradeHandler()).thenReturn(mock(WebSocketUpgradeHandler.class));
        when(deps.httpRouter()).thenReturn(router);
        return new NettyHttpServerHandler(null, null, deps);
    }

    /**
     * Handler whose routing BLOCKS until released: request processing runs on a virtual thread, so
     * an unsynchronized assertion races the response write (whose listener clears the in-flight
     * flag). The gate turns that race into a deterministic sequence: enter -> assert mid-flight
     * state -> release -> await completion.
     */
    private static final class GatedRouter implements HttpRouter {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void route(HttpContext ctx) throws Exception {
            entered.countDown();
            release.await(5, java.util.concurrent.TimeUnit.SECONDS);
            ctx.text(com.github.dropguard.summer.web.HttpStatus.OK, "done");
        }

        void awaitEntered() {
            try {
                assertTrue(
                        entered.await(5, java.util.concurrent.TimeUnit.SECONDS),
                        "handler never started processing the request");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }

        void releaseAndAwaitCompletion(EmbeddedChannel channel) {
            release.countDown();
            for (int i = 0; i < 500 && ChannelInflight.isActive(channel); i++) {
                channel.runPendingTasks();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private EmbeddedChannel newChannel(HttpRouter router) {
        return NettyTestSupport.newChannel(
                new IdleStateHandler(60_000, 0, 0),
                new ReadTimeoutHandler(10_000),
                new HttpObjectAggregator(1024),
                newHandler(router));
    }

    private static FullHttpRequest getRequest() {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello");
    }

    @Test
    void readTimeoutIsRemovedOnceRequestIsFullyReceived() {
        GatedRouter router = new GatedRouter();
        EmbeddedChannel channel = newChannel(router);
        assertNotNull(channel.pipeline().get(ReadTimeoutHandler.class));

        channel.writeInbound(getRequest());
        router.awaitEntered();

        // Deterministic mid-flight window: request fully received, response deliberately held.
        assertTrue(
                ChannelInflight.isActive(channel),
                "fully-received request marks the channel in-flight");
        assertNull(
                channel.pipeline().get(ReadTimeoutHandler.class),
                "read timeout protects request reception only \u2014 it must not fire "
                        + "during streaming responses");

        router.releaseAndAwaitCompletion(channel);
        assertFalse(ChannelInflight.isActive(channel), "completed response clears the flag");
        channel.finishAndReleaseAll();
    }

    @Test
    void idleEventIsIgnoredWhileResponseWorkIsInFlight() {
        GatedRouter router = new GatedRouter();
        EmbeddedChannel channel = newChannel(router);
        channel.writeInbound(getRequest());
        router.awaitEntered();

        // Open stream mid-response: a quiet read side is NOT idleness.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        assertTrue(channel.isOpen(), "in-flight channels must survive reader-idle events");

        router.releaseAndAwaitCompletion(channel);
        channel.finishAndReleaseAll();
    }

    /**
     * A connection may only ever have ONE request in flight: a pipelined request arriving while a
     * previous handler is still running must never dispatch concurrently — two in-flight virtual
     * threads would race their writes and the second response could leave before the first (RFC
     * 9112 §7.6 keeps responses strictly ordered). The server's answer is to close the connection;
     * the client reissues on a fresh connection and the in-flight response is never interleaved.
     */
    @Test
    void pipelinedRequestWhileHandlerIsBusyClosesConnection() {
        GatedRouter router = new GatedRouter();
        EmbeddedChannel channel = newChannel(router);

        channel.writeInbound(getRequest());
        router.awaitEntered();
        assertTrue(channel.isOpen(), "first request is being handled");

        try {
            channel.writeInbound(getRequest());
            channel.runPendingTasks(); // EmbeddedChannel close() is queued, not synchronous
            assertFalse(
                    channel.isOpen(),
                    "a second request on the same connection must not run concurrently"
                            + " with the first — the connection is closed instead");
        } finally {
            router.release.countDown();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * The pipelining gate must never leak into ordinary keep-alive reuse: after the first response
     * is fully written (flag cleared), the same connection is available for the next request, and
     * responses leave strictly in request order. This is the green half of the gate's red-green
     * pair — the red half ({@link #pipelinedRequestWhileHandlerIsBusyClosesConnection}) proves a
     * busy connection is refused; this one proves the refusal state is fully cleared.
     */
    @Test
    void sequentialKeepAliveRequestsCompleteInOrder() {
        java.util.concurrent.atomic.AtomicInteger seq =
                new java.util.concurrent.atomic.AtomicInteger();
        EmbeddedChannel channel =
                newChannel(ctx -> ctx.text(HttpStatus.OK, "resp-" + seq.incrementAndGet()));

        channel.writeInbound(getRequest());
        awaitChannelIdle(channel);
        FullHttpResponse first = channel.readOutbound();
        try {
            assertEquals(HttpResponseStatus.OK, first.status(), "first request's response");
            assertEquals(
                    "resp-1", first.content().toString(java.nio.charset.StandardCharsets.UTF_8));

            channel.writeInbound(getRequest());
            awaitChannelIdle(channel);
            FullHttpResponse second = channel.readOutbound();
            try {
                assertEquals(HttpResponseStatus.OK, second.status(), "second request's response");
                assertEquals(
                        "resp-2",
                        second.content().toString(java.nio.charset.StandardCharsets.UTF_8),
                        "responses must leave in request order — the second request was only"
                                + " dispatched after the first response fully completed");
            } finally {
                second.release();
            }
        } finally {
            first.release();
            channel.finishAndReleaseAll();
        }
    }

    /** Waits until the channel's in-flight flag clears, pumping embedded tasks. */
    private static void awaitChannelIdle(EmbeddedChannel channel) {
        for (int i = 0; i < 500 && ChannelInflight.isActive(channel); i++) {
            channel.runPendingTasks();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        assertFalse(ChannelInflight.isActive(channel), "channel must return to idle");
    }

    @Test
    void idleEventClosesAGenuineKeepAliveGap() {
        EmbeddedChannel channel = newChannel(new GatedRouter());
        // No request ever arrived: the channel sits in the pre-request idle state.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        assertFalse(channel.isOpen(), "dead keep-alive connections must be reclaimed");

        // And after a completed buffered response the same applies again.
        // No-op routing: dispatch marks in-flight synchronously; the explicit sink write below
        // completes the lifecycle on this thread — no virtual-thread involvement.
        EmbeddedChannel active = newChannel(ctx -> {});
        active.writeInbound(getRequest());
        active.runPendingTasks();
        ChannelHandlerContext sinkCtx = active.pipeline().context(NettyHttpServerHandler.class);
        new NettyResponseSink(sinkCtx, true)
                .sendBytes(HttpStatus.OK, java.util.Map.of(), new byte[0]);
        assertFalse(
                ChannelInflight.isActive(active),
                "completed keep-alive response returns the channel to idle detection");
        assertTrue(active.isOpen());
        active.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        assertFalse(active.isOpen(), "post-response gap is genuinely idle -> close");
    }
}
