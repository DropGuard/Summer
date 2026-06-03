package summer.example.controller;

import summer.core.Component;
import summer.web.WsRouter;
import summer.web.websocket.WebSocketBroadcaster;
import summer.web.websocket.WebSocketContext;
import summer.web.websocket.WebSocketHandler;

@Component
public class ChatWebSocketHandler implements WebSocketHandler {

	private final WebSocketBroadcaster broadcaster;

	public ChatWebSocketHandler(WsRouter wsRouter, WebSocketBroadcaster broadcaster) {
		this.broadcaster = broadcaster;
		// Automatically register this handler to the router
		wsRouter.ws("/chat/{room}", this);
	}

	@Override
	public void handle(WebSocketContext ctx) {
		String room = ctx.pathParam("room");

		// Join the room upon connection
		broadcaster.join(room, ctx);
		broadcaster.broadcast(room, "[System] A new user joined the room: " + room);

		// Handle incoming messages
		ctx.onMessage(msg -> {
			// Broadcast the message to all clients in the same room
			broadcaster.broadcast(room, "Echo: " + msg);
		});

		// Handle disconnection
		ctx.onClose(() -> {
			broadcaster.leave(room, ctx);
			broadcaster.broadcast(room, "[System] A user left the room: " + room);
		});
	}
}
