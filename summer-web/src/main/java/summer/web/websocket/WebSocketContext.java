package summer.web.websocket;

import java.util.function.Consumer;

public interface WebSocketContext {
	String pathParam(String name);
	String header(String name);
	void send(String text);
	void onMessage(Consumer<String> consumer);
	void onClose(Runnable onClose);
	<T> void onMessageAs(Class<T> type, Consumer<T> consumer);
	void sendJson(Object payload);
	void close();
}
