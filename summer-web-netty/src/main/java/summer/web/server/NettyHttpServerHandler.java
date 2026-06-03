package summer.web.server;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import summer.web.BodyConverter;
import summer.web.Handler;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.HttpRouter;
import summer.web.Request;
import summer.web.ServerConfig;
import summer.web.WsRouter;
import summer.web.middleware.Middleware;

public class NettyHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final Logger log = LoggerFactory.getLogger(NettyHttpServerHandler.class);

	private final HttpRouter httpRouter;
	private final WsRouter wsRouter;
	private final List<Middleware> middlewares;
	private final BodyConverter jsonConverter;
	private final NettyHttpServer server;
	private final ServerConfig config;

	public NettyHttpServerHandler(HttpRouter httpRouter, WsRouter wsRouter, List<Middleware> middlewares,
			BodyConverter jsonConverter, NettyHttpServer server, ServerConfig config) {
		this.httpRouter = httpRouter;
		this.wsRouter = wsRouter;
		this.middlewares = middlewares;
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

			WsRouter.WsMatch wsMatch = wsRouter.routeWs(path);
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
			HttpContext webCtx = new HttpContext(request, jsonConverter);

			Handler handler = createHandlerChain();
			handler.handle(webCtx);

			if (webCtx.statusCode() == null) {
				webCtx.text(HttpStatus.NOT_FOUND, "Not Found");
			}

			sendResponse(ctx, webCtx, keepAlive);

		} catch (Exception e) {
			HttpContext errCtx = new HttpContext(NettyRequestAdapter.adapt(nettyReq), jsonConverter);
			errCtx.error(e);
			sendResponse(ctx, errCtx, keepAlive);
		}
	}

	private Handler createHandlerChain() {
		Handler dispatchHandler = (c) -> {
			try {
				httpRouter.route(c);
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

	private void sendResponse(ChannelHandlerContext ctx, HttpContext webCtx, boolean keepAlive) {
		HttpResponseStatus status = HttpResponseStatus.valueOf(webCtx.statusCode().code());

		FullHttpResponse nettyResp;
		if (webCtx.resultObject() != null && webCtx.converter() != null) {
			nettyResp = serializeResponse(ctx, webCtx, status, keepAlive);
			if (nettyResp == null) {
				return; // Error response already sent
			}
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

	private FullHttpResponse serializeResponse(ChannelHandlerContext ctx, HttpContext webCtx,
			HttpResponseStatus status, boolean keepAlive) {
		io.netty.buffer.ByteBuf buf = ctx.alloc().directBuffer();
		try (io.netty.buffer.ByteBufOutputStream out = new io.netty.buffer.ByteBufOutputStream(buf)) {
			webCtx.converter().writeToStream(webCtx.resultObject(), out);
		} catch (Exception e) {
			buf.release();
			log.error("Serialization error", e);
			byte[] errorBytes = "Internal Server Error".getBytes(java.nio.charset.StandardCharsets.UTF_8);
			FullHttpResponse errorResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
					HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer(errorBytes));
			errorResp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
			errorResp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, errorBytes.length);
			if (keepAlive) {
				errorResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
				ctx.writeAndFlush(errorResp);
			} else {
				errorResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
				ctx.writeAndFlush(errorResp).addListener(ChannelFutureListener.CLOSE);
			}
			return null;
		}
		return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		log.error("Channel exception", cause);
		ctx.close();
	}
}
