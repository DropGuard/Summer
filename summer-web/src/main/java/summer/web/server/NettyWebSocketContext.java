package summer.web.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.Map;
import java.util.function.Consumer;
import summer.web.websocket.WebSocketContext;

public class NettyWebSocketContext implements WebSocketContext {

	private final ChannelHandlerContext ctx;
	private final Map<String, String> pathParams;
	private Consumer<String> messageConsumer;
	private Runnable closeHandler;

	public NettyWebSocketContext(ChannelHandlerContext ctx, Map<String, String> pathParams) {
		this.ctx = ctx;
		this.pathParams = pathParams;
	}

	public ChannelHandlerContext getChannelHandlerContext() {
		return ctx;
	}

	@Override
	public String pathParam(String name) {
		return pathParams.get(name);
	}

	@Override
	public void send(String text) {
		if (ctx.channel().isActive()) {
			ctx.writeAndFlush(new TextWebSocketFrame(text));
		}
	}

	@Override
	public void onMessage(Consumer<String> consumer) {
		this.messageConsumer = consumer;
	}

	@Override
	public void onClose(Runnable onClose) {
		this.closeHandler = onClose;
	}

	// Internal methods invoked by SummerWebSocketFrameHandler

	void invokeMessageConsumer(String message) {
		if (messageConsumer != null) {
			messageConsumer.accept(message);
		}
	}

	void invokeCloseHandler() {
		if (closeHandler != null) {
			closeHandler.run();
		}
	}
}
