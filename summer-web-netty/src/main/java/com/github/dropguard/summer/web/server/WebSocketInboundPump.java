package com.github.dropguard.summer.web.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-connection inbound pipeline for WebSocket messages: a bounded buffer plus one serialized
 * virtual thread that drains it.
 *
 * <p>The event loop thread only enqueues frames ({@link #accept}) and toggles reads: once the
 * buffer reaches the high watermark, {@code autoRead} is suspended so TCP backpressure stops the
 * peer from sending; when the consumer has drained the buffer back to the low watermark, reads
 * resume. A slow or blocking message handler therefore stalls only its own connection — never the
 * event loop, other WebSocket connections, or HTTP traffic. Frames already in flight when reads are
 * suspended can still overflow the buffer; that closes the connection with a 1008 (Policy
 * Violation) close frame rather than growing memory without bound.
 *
 * <p>The same virtual thread runs the registration callback first and then the drain loop, so
 * messages, the close event and registration code never run concurrently for one connection and
 * user code never executes on the event loop. The close event is queued like a message — {@code
 * onClose} fires after every buffered message has been delivered.
 */
final class WebSocketInboundPump {

    private static final Logger log = LoggerFactory.getLogger(WebSocketInboundPump.class);

    /**
     * Close code sent when frames already in flight overflow the buffer: the peer kept sending past
     * the suspended reads, so the connection is dropped loudly instead of buffering without bound
     * (RFC 6455: policy violation).
     */
    static final int POLICY_VIOLATION = 1008;

    private sealed interface InboundEvent permits MessageEvent, CloseEvent {}

    private record MessageEvent(String text) implements InboundEvent {}

    private record CloseEvent() implements InboundEvent {}

    private static final CloseEvent CLOSE_EVENT = new CloseEvent();

    private final NettyWebSocketContext wsContext;
    private final Channel channel;
    // One spare slot beyond the configured message capacity is reserved for the close event so
    // queueing it can never fail, even with the buffer full at connection teardown.
    private final BlockingQueue<InboundEvent> queue;
    private final int messageCapacity;
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
        this.messageCapacity = messageCapacity;
        this.queue = new ArrayBlockingQueue<>(messageCapacity + 1);
        this.highWatermark = Math.max(1, (messageCapacity * 3) / 4);
        this.lowWatermark = Math.max(1, messageCapacity / 2);
    }

    /**
     * Event-loop side: buffer one inbound text frame. Suspends reads at the high watermark; a
     * message arriving past the capacity (only possible for frames already in flight) closes the
     * connection with 1008.
     */
    void accept(String message) {
        if (consumerFinished || !channel.isActive()) {
            return;
        }
        if (queue.size() >= messageCapacity) {
            overflowClose();
            return;
        }
        // Only the event loop produces messages, so the capacity check above makes this offer
        // infallible; the spare slot stays reserved for the close event.
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

    private void overflowClose() {
        log.warn(
                "WebSocket inbound buffer overflow ({} buffered messages); closing {} with {}",
                queue.size(),
                channel.remoteAddress(),
                POLICY_VIOLATION);
        channel.writeAndFlush(new CloseWebSocketFrame(POLICY_VIOLATION, "inbound buffer overflow"))
                .addListener(ChannelFutureListener.CLOSE);
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
