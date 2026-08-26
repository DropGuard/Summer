package com.github.dropguard.summer.web.websocket;

/**
 * Broadcasts messages to connected WebSocket clients.
 *
 * <p><strong>Delivery scope: this JVM instance only.</strong> A broadcaster reaches exactly the
 * connections held by the current process — {@code broadcastAll} means all connections of this
 * instance, not of a cluster. For multi-instance deployments, replace this bean ({@code @Replaces}
 * on the framework's provider) with an implementation backed by an external pub/sub system (Redis,
 * Kafka, ...): fan messages out through the external bus, and deliver locally on each instance upon
 * receipt. The interface is the supported seam for that upgrade; the default in-memory
 * implementation is the single-node starting point.
 *
 * <p>Lifecycle model: the transport registers every established session via {@link #connected};
 * room membership is explicit opt-in via {@link #join}. The two are independent — a client that
 * never joined a room still receives {@link #broadcastAll}, and leaving a room never removes a live
 * client from global delivery.
 */
public interface WebSocketBroadcaster {

    /**
     * Transport hook: called once when a WebSocket session is fully established. Implementations
     * may use it to track live sessions for {@link #broadcastAll}; doing so is optional (default is
     * a no-op), but skipping it means unjoined clients receive nothing.
     */
    default void connected(WebSocketContext ctx) {}

    /** Adds the given WebSocketContext to a specified room. */
    void join(String room, WebSocketContext ctx);

    /**
     * Removes the given WebSocketContext from the specified room. Scope note: this affects room
     * delivery ONLY — the client remains registered as a live connection and keeps receiving {@link
     * #broadcastAll}.
     */
    void leave(String room, WebSocketContext ctx);

    /** Broadcasts a message to all WebSocket connections in the specified room. */
    void broadcast(String room, String message);

    /** Broadcasts a message to every live WebSocket connection of this instance. */
    void broadcastAll(String message);
}
