package summer.web.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.validation.BodyValidator;
import summer.web.BodyConverter;
import summer.web.Handler;
import summer.web.HttpStatus;
import summer.web.Request;
import summer.web.Router;
import summer.web.ServerConfig;
import summer.web.WebContext;
import summer.web.middleware.Middleware;

public class NettyHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final Logger log = LoggerFactory.getLogger(NettyHttpServerHandler.class);

	private final Router router;
	private final List<Middleware> middlewares;
	private final BodyValidator validator;
	private final BodyConverter jsonConverter;
	private final NettyHttpServer server;
	private final ServerConfig config;

	public NettyHttpServerHandler(Router router, List<Middleware> middlewares, BodyValidator validator,
			BodyConverter jsonConverter, NettyHttpServer server, ServerConfig config) {
		this.router = router;
		this.middlewares = middlewares;
		this.validator = validator;
		this.jsonConverter = jsonConverter;
		this.server = server;
		this.config = config;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest nettyReq) {
		if (server != null)
			server.getActiveConnections().incrementAndGet();

		// Check for WebSocket Upgrade
		if (nettyReq.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)) {
			String uri = nettyReq.uri();
			String path = uri;
			int questionMarkIndex = uri.indexOf('?');
			if (questionMarkIndex != -1) {
				path = uri.substring(0, questionMarkIndex);
			}

			// Validate Origin header to prevent CSWSH attacks
			String origin = nettyReq.headers().get(HttpHeaderNames.ORIGIN);
			String host = nettyReq.headers().get(HttpHeaderNames.HOST);
			if (!config.isOriginAllowed(origin, host)) {
				log.warn("WebSocket connection rejected: origin '{}' not allowed for host '{}'", origin, host);
				sendSimpleResponse(ctx, HttpResponseStatus.FORBIDDEN, "Origin not allowed");
				if (server != null)
					server.getActiveConnections().decrementAndGet();
				return;
			}

			Router.WsMatch wsMatch = router.routeWs(path);
			if (wsMatch != null) {
				// It's a valid WebSocket route. Set up the context.
				NettyWebSocketContext wsContext = new NettyWebSocketContext(ctx, wsMatch.pathParams);

				// Initialize the user's handler which will register onMessage/onClose callbacks
				wsMatch.handler.handle(wsContext);

				// Dynamically modify the Netty pipeline for WebSocket
				ctx.pipeline().addLast(
						new io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler(uri, null, true));
				ctx.pipeline().addLast(new SummerWebSocketFrameHandler(wsContext));

				// Retain and pass the request to WebSocketServerProtocolHandler to complete the
				// handshake
				ctx.fireChannelRead(nettyReq.retain());

				if (server != null)
					server.getActiveConnections().decrementAndGet();
				return; // Do NOT hand off to Virtual Thread
			}
		}

		// 1. Check HTTP Keep-Alive
		boolean keepAlive = HttpUtil.isKeepAlive(nettyReq);

		// 2. Retain the request because we are handing it off to another thread
		nettyReq.retain();

		// 3. Dispatch to Virtual Thread (Loom)
		Thread.startVirtualThread(() -> {
			try {
				processRequest(ctx, nettyReq, keepAlive);
			} finally {
				// Must release the retained Netty buffer once done processing
				nettyReq.release();
				if (server != null)
					server.getActiveConnections().decrementAndGet();
			}
		});
	}

	private void sendSimpleResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
		byte[] bytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
				Unpooled.wrappedBuffer(bytes));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
		response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private void processRequest(ChannelHandlerContext ctx, FullHttpRequest nettyReq, boolean keepAlive) {
		try {
			Request request = NettyRequestAdapter.adapt(nettyReq);
			WebContext webCtx = new WebContext(request, validator, jsonConverter);

			Handler handler = createHandlerChain();
			handler.handle(webCtx);

			if (webCtx.statusCode() == null) {
				webCtx.text(HttpStatus.NOT_FOUND, "Not Found");
			}

			sendResponse(ctx, webCtx, keepAlive);

		} catch (Exception e) {
			WebContext errCtx = new WebContext(NettyRequestAdapter.adapt(nettyReq), validator, jsonConverter);
			errCtx.error(e);
			sendResponse(ctx, errCtx, keepAlive);
		}
	}

	private Handler createHandlerChain() {
		Handler dispatchHandler = (c) -> {
			try {
				router.route(c);
			} catch (Exception e) {
				c.error(e);
			}
			return null;
		};

		Handler handler = dispatchHandler;
		for (Middleware middleware : middlewares) {
			handler = middleware.apply(handler);
		}

		return handler;
	}

	private void sendResponse(ChannelHandlerContext ctx, WebContext webCtx, boolean keepAlive) {
		HttpResponseStatus status = HttpResponseStatus.valueOf(webCtx.statusCode().code());

		FullHttpResponse nettyResp;
		if (webCtx.resultObject() != null && webCtx.converter() != null) {
			io.netty.buffer.ByteBuf buf = ctx.alloc().directBuffer();
			try (io.netty.buffer.ByteBufOutputStream out = new io.netty.buffer.ByteBufOutputStream(buf)) {
				webCtx.converter().writeToStream(webCtx.resultObject(), out);
			} catch (Exception e) {
				buf.release();
				log.error("Serialization error", e);
				byte[] errorBytes = "Internal Server Error".getBytes(java.nio.charset.StandardCharsets.UTF_8);
				nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR,
						Unpooled.wrappedBuffer(errorBytes));
				nettyResp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
				nettyResp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, errorBytes.length);
				if (keepAlive) {
					nettyResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
					ctx.writeAndFlush(nettyResp);
				} else {
					nettyResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
					ctx.writeAndFlush(nettyResp).addListener(ChannelFutureListener.CLOSE);
				}
				return;
			}
			nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
		} else if (webCtx.body() != null && webCtx.body().length > 0) {
			nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
					Unpooled.wrappedBuffer(webCtx.body()));
		} else {
			nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
		}

		// Apply headers
		for (Map.Entry<String, String> entry : webCtx.headers().entrySet()) {
			nettyResp.headers().set(entry.getKey(), entry.getValue());
		}

		if (keepAlive) {
			if (!nettyResp.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
				nettyResp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, nettyResp.content().readableBytes());
			}
			nettyResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.writeAndFlush(nettyResp);
		} else {
			nettyResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
			ctx.writeAndFlush(nettyResp).addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		log.error("Channel exception", cause);
		ctx.close();
	}
}
