package com.github.dropguard.summer.tck.web.fixtures;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.WsRouteProvider;
import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket route provider for the transport backpressure integration tests. Registered as a
 * {@code @Component} (not a {@code @Bean}) so its bean name is the concrete class — multiple {@code
 * WsRouteProvider} implementations can coexist in the test universe and are all collected by {@code
 * NettyServerRunner}.
 *
 * <p>The test arms a fresh {@link Gate} before opening a connection; the static reference is
 * resolved by the wired handlers at message time, so re-arming between test invocations is enough.
 */
@Component
public class BackpressureWsRouteProvider implements WsRouteProvider {

    private static final AtomicReference<Gate> GATE = new AtomicReference<>();

    /** Arms the gate the wired handlers will drive; call before opening the connection. */
    public static void arm(Gate gate) {
        GATE.set(gate);
    }

    @Override
    public void provide(WsRouter.Builder builder) {
        builder.ws(
                "/ws-backpressure",
                ctx -> {
                    ctx.onMessage(
                            msg -> {
                                Gate gate = GATE.get();
                                if (gate != null) {
                                    gate.onMessage(msg, ctx);
                                }
                            });
                    ctx.onClose(
                            () -> {
                                Gate gate = GATE.get();
                                if (gate != null) {
                                    gate.close();
                                }
                            });
                });
    }

    /**
     * Mutable per-test state. The first message parks the connection's handler thread inside the
     * gate — that is what turns the connection into a slow consumer — and every message is
     * acknowledged back so the client can observe ordered delivery after the gate opens.
     */
    public static final class Gate {

        private final CountDownLatch firstMessageArrived = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final List<String> acked = new CopyOnWriteArrayList<>();
        private volatile Thread handlerThread;

        void onMessage(String msg, WebSocketContext ctx) {
            handlerThread = Thread.currentThread();
            if (firstMessageArrived.getCount() > 0) {
                firstMessageArrived.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            acked.add(msg);
            ctx.send("ACK " + msg);
        }

        void close() {
            closed.countDown();
        }

        /**
         * The thread that executed the message handler — the pump thread under the new contract.
         */
        public Thread handlerThread() {
            return handlerThread;
        }

        public void awaitFirstMessage() throws InterruptedException {
            if (!firstMessageArrived.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("server never received the first message");
            }
        }

        public void open() {
            release.countDown();
        }

        /**
         * @return true if the close handler fired within the timeout.
         */
        public boolean awaitClosed(long timeoutMs) throws InterruptedException {
            return closed.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        public List<String> acked() {
            return List.copyOf(acked);
        }
    }
}
