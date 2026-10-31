package com.github.dropguard.summer.web.server;

import io.netty.channel.Channel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-connection inbound pipeline for WebSocket messages: a buffer plus one serialized virtual
 * thread that drains it.
 *
 * <p>The event loop thread only enqueues frames ({@link #accept}) and toggles reads: once the
 * buffer reaches the high watermark, {@code autoRead} is suspended so TCP backpressure stops the
 * peer from sending; when the consumer has drained the buffer back to the low watermark, reads
 * resume. A slow or blocking message handler therefore stalls only its own connection — never the
 * event loop, other WebSocket connections, or HTTP traffic.
 *
 * <p>Frames already decoded when reads are suspended are buffered rather than rejected: they are
 * bounded by the one read batch Netty was mid-way through (per-read allocations are capped well
 * below a megabyte), so suspension alone bounds memory and there is deliberately no overflow close
 * — TCP cannot deliver what the server does not read, so a well-behaved backpressured peer must
 * never be punished for arriving in a batch.
 *
 * <p>The same virtual thread runs the registration callback first and then the drain loop, so
 * messages, the close event and registration code never run concurrently for one connection and
 * user code never executes on the event loop. The close event is queued like a message — {@code
 * onClose} fires after every buffered message has been delivered.
 */
final class WebSocketInboundPump {

    private static final Logger log = LoggerFactory.getLogger(WebSocketInboundPump.class);

    private sealed interface InboundEvent permits MessageEvent, CloseEvent {}

    private record MessageEvent(String text) implements InboundEvent {}

    private record CloseEvent() implements InboundEvent {}

    private static final CloseEvent CLOSE_EVENT = new CloseEvent();

    private final NettyWebSocketContext wsContext;
    private final Channel channel;
    // Effectively bounded by the read suspension above: steady state holds at most the
    // high-watermark count, plus whatever one read batch had already decoded. Unbounded capacity
    // keeps accept() infallible — including the close event at connection teardown.
    private final BlockingQueue<InboundEvent> queue;
    private final int highWatermark;
    private final int lowWatermark;
    private final AtomicBoolean readsSuspended = new AtomicBoolean();
    private final AtomicBoolean closeQueued = new AtomicBoolean();
    private volatile boolean consumerFinished;

    WebSocketInboundPump(NettyWebSocketContext wsContext, Channel channel, int messageCapacity) {
        if (messageCapacity < 1) {
            throw new IllegalArgumentException("wsMessageQueueCapacity must be >= 1");
        }
        this.wsContext = wsContext;
        this.channel = channel;
        this.queue = new LinkedBlockingQueue<>();
        this.highWatermark = Math.max(1, (messageCapacity * 3) / 4);
        this.lowWatermark = Math.max(1, messageCapacity / 2);
    }

    /**
     * Event-loop side: buffer one inbound text frame. Suspends reads at the high watermark; frames
     * decoded within the same read batch keep flowing into the buffer (bounded by the batch) and
     * are delivered once the consumer catches up.
     */
    void accept(String message) {
        if (consumerFinished || !channel.isActive()) {
            return;
        }
        queue.offer(new MessageEvent(message));
        if (queue.size() >= highWatermark && readsSuspended.compareAndSet(false, true)) {
            channel.config().setAutoRead(false);
        }
    }

    /**
     * Event-loop side: the connection went down. Queues the close event so it is delivered after
     * every message buffered before the disconnect; the consumer therefore always terminates.
     */
    void connectionClosed() {
        if (consumerFinished || closeQueued.getAndSet(true)) {
            return;
        }
        queue.offer(CLOSE_EVENT);
    }

    /**
     * Runs on the connection's virtual thread when the registration callback itself failed: no
     * message was ever accepted, so the close handler fires immediately — still on this thread —
     * and the connection goes.
     */
    void registrationFailed() {
        consumerFinished = true;
        try {
            wsContext.invokeCloseHandler();
        } catch (Exception e) {
            log.error("WebSocket close handler failed after registration failure", e);
        }
        channel.close();
    }

    /**
     * Drains the buffer until the close event. Blocks the calling (virtual) thread for the lifetime
     * of the connection. An exception from a message handler or interceptor closes the connection;
     * remaining buffered messages are dropped, matching the previous exceptionCaught-and-close
     * behaviour, and {@code onClose} still fires.
     */
    void drain() {
        if (consumerFinished) {
            return;
        }
        try {
            while (true) {
                InboundEvent event = queue.take();
                if (event instanceof CloseEvent) {
                    consumerFinished = true;
                    invokeCloseHandler();
                    return;
                }
                try {
                    wsContext.invokeMessageConsumer(((MessageEvent) event).text());
                } catch (Exception e) {
                    consumerFinished = true;
                    log.error(
                            "WebSocket message handler failed; closing connection {}",
                            channel.remoteAddress(),
                            e);
                    channel.close();
                    invokeCloseHandler();
                    return;
                }
                resumeReadsIfDrained();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void resumeReadsIfDrained() {
        if (readsSuspended.get()
                && queue.size() <= lowWatermark
                && readsSuspended.compareAndSet(true, false)) {
            channel.config().setAutoRead(true);
        }
    }

    private void invokeCloseHandler() {
        try {
            wsContext.invokeCloseHandler();
        } catch (Exception e) {
            log.error("WebSocket close handler failed for {}", channel.remoteAddress(), e);
        }
    }
}
