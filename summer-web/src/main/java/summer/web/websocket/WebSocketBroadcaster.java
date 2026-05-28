package summer.web.websocket;

/**
 * Provides methods for broadcasting messages to groups/rooms of connected
 * WebSocket clients.
 */
public interface WebSocketBroadcaster {

	/**
	 * Adds the given WebSocketContext to a specified room.
	 */
	void join(String room, WebSocketContext ctx);

	/**
	 * Removes the given WebSocketContext from a specified room.
	 */
	void leave(String room, WebSocketContext ctx);

	/**
	 * Broadcasts a message to all WebSocket connections in the specified room.
	 */
	void broadcast(String room, String message);

	/**
	 * Broadcasts a message to all active WebSocket connections across all rooms.
	 */
	void broadcastAll(String message);
}
