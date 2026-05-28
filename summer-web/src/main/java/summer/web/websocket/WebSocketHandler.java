package summer.web.websocket;

@FunctionalInterface
public interface WebSocketHandler {
	void handle(WebSocketContext ctx);
}
