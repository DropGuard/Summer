package summer.web.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import summer.web.websocket.WebSocketContext;
import summer.web.websocket.WsFilterChain;
import summer.web.websocket.WsInterceptor;

public class NettyWebSocketContext implements WebSocketContext {

	private final ChannelHandlerContext ctx;
	private final Map<String, String> pathParams;
	private final List<WsInterceptor> wsInterceptors;
	private Consumer<String> messageConsumer;
	private Runnable closeHandler;

	public NettyWebSocketContext(ChannelHandlerContext ctx, Map<String, String> pathParams,
			List<WsInterceptor> wsInterceptors) {
		this.ctx = ctx;
		this.pathParams = pathParams;
		this.wsInterceptors = wsInterceptors;
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
			WsFilterChain chain = new WsFilterChain() {
				private int index = 0;

				@Override
				public void doFilter(WebSocketContext ctx, String msg) {
					if (wsInterceptors != null && index < wsInterceptors.size()) {
						WsInterceptor interceptor = wsInterceptors.get(index++);
						interceptor.intercept(ctx, msg, this);
					} else {
						messageConsumer.accept(msg);
					}
				}
			};
			chain.doFilter(this, message);
		}
	}

	void invokeCloseHandler() {
		if (closeHandler != null) {
			closeHandler.run();
		}
	}
}
