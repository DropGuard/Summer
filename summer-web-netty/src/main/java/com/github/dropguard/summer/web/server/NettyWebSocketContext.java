package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.BodyConverter;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import com.github.dropguard.summer.web.websocket.WsFilterChain;
import com.github.dropguard.summer.web.websocket.WsInterceptor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class NettyWebSocketContext implements WebSocketContext {

	private final ChannelHandlerContext ctx;
	private final Map<String, String> pathParams;
	private final Map<String, String> headers;
	private final List<WsInterceptor> wsInterceptors;
	private final BodyConverter jsonConverter;
	private Consumer<String> messageConsumer;
	private Runnable closeHandler;

	public NettyWebSocketContext(ChannelHandlerContext ctx, Map<String, String> pathParams, Map<String, String> headers,
			List<WsInterceptor> wsInterceptors, BodyConverter jsonConverter) {
		this.ctx = ctx;
		this.pathParams = pathParams;
		this.headers = headers;
		this.wsInterceptors = wsInterceptors;
		this.jsonConverter = jsonConverter;
	}

	public ChannelHandlerContext getChannelHandlerContext() {
		return ctx;
	}

	@Override
	public String pathParam(String name) {
		return pathParams.get(name);
	}
	@Override
	public String header(String name) {
		return headers.get(name.toLowerCase());
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
	public <T> void onMessageAs(Class<T> type, Consumer<T> consumer) {
		this.messageConsumer = text -> {
			try {
				T obj = jsonConverter.read(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), type);
				consumer.accept(obj);
			} catch (java.io.IOException e) {
				ctx.channel().pipeline().fireExceptionCaught(e);
			}
		};
	}

	@Override
	public void sendJson(Object payload) {
		try {
			byte[] jsonBytes = jsonConverter.write(payload);
			send(new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8));
		} catch (java.io.IOException e) {
			throw new RuntimeException("Failed to serialize WebSocket message", e);
		}
	}
	@Override
	public void close() {
		ctx.close();
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
