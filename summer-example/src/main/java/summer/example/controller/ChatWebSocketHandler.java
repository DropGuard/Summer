package summer.example.controller;

import summer.core.Component;
import summer.core.annotation.ConditionalOnBean;
import summer.web.WsRouter;
import summer.web.WsRouteProvider;
import summer.web.websocket.WebSocketBroadcaster;
import summer.web.websocket.WebSocketContext;
import summer.web.websocket.WebSocketHandler;

@Component
@ConditionalOnBean(WebSocketBroadcaster.class)
public class ChatWebSocketHandler implements WsRouteProvider, WebSocketHandler {

	private final WebSocketBroadcaster broadcaster;

	public ChatWebSocketHandler(WebSocketBroadcaster broadcaster) {
		this.broadcaster = broadcaster;
	}

	@Override
	public void provide(WsRouter.Builder builder) {
		builder.ws("/chat/{room}", this);
	}

	@Override
	public void handle(WebSocketContext ctx) {
		String room = ctx.pathParam("room");

		broadcaster.join(room, ctx);
		broadcaster.broadcast(room, "[System] A new user joined the room: " + room);

		ctx.onMessage(msg -> {
			broadcaster.broadcast(room, "Echo: " + msg);
		});

		ctx.onClose(() -> {
			broadcaster.leave(room, ctx);
			broadcaster.broadcast(room, "[System] A user left the room: " + room);
		});
	}
}
