package summer.web.websocket;

import java.util.function.Consumer;

public interface WebSocketContext {
	String pathParam(String name);
	void send(String text);
	void onMessage(Consumer<String> consumer);
	void onClose(Runnable onClose);
}
