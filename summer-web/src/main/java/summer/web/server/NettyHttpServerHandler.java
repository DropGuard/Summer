package summer.web.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import java.util.List;
import java.util.Map;
import summer.validation.BodyValidator;
import summer.web.BodyConverter;
import summer.web.Handler;
import summer.web.Request;
import summer.web.Response;
import summer.web.Router;
import summer.web.WebContext;
import summer.web.middleware.Middleware;

public class NettyHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private final Router router;
	private final List<Middleware> middlewares;
	private final BodyValidator validator;
	private final List<BodyConverter> converters;
	private final NettyHttpServer server;

	public NettyHttpServerHandler(Router router, List<Middleware> middlewares, BodyValidator validator,
			List<BodyConverter> converters, NettyHttpServer server) {
		this.router = router;
		this.middlewares = middlewares;
		this.validator = validator;
		this.converters = converters;
		this.server = server;
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

	private void processRequest(ChannelHandlerContext ctx, FullHttpRequest nettyReq, boolean keepAlive) {
		try {
			Request request = NettyRequestAdapter.adapt(nettyReq);
			Response response = new Response();
			WebContext webCtx = new WebContext(request, response, validator, converters);

			Handler handler = createHandlerChain();
			handler.handle(webCtx);

			if (!response.isCommitted()) {
				response.notFound();
			}

			sendResponse(ctx, response, keepAlive);

		} catch (Exception e) {
			e.printStackTrace();
			Response errResponse = new Response();
			errResponse.error(e);
			sendResponse(ctx, errResponse, keepAlive);
		}
	}

	private Handler createHandlerChain() {
		Handler dispatchHandler = (c) -> {
			try {
				Object result = router.route(c);
				if (result != null) {
					c.ok(result);
				} else {
					c.notFound();
				}
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

	private void sendResponse(ChannelHandlerContext ctx, Response response, boolean keepAlive) {
		HttpResponseStatus status = HttpResponseStatus.valueOf(response.getStatusCode());

		FullHttpResponse nettyResp;
		if (response.getResultObject() != null && response.getConverter() != null) {
			io.netty.buffer.ByteBuf buf = ctx.alloc().directBuffer();
			try (io.netty.buffer.ByteBufOutputStream out = new io.netty.buffer.ByteBufOutputStream(buf)) {
				response.getConverter().writeToStream(response.getResultObject(), out);
			} catch (Exception e) {
				buf.release();
				e.printStackTrace();
				byte[] errorBytes = ("Serialization Error: " + e.getMessage())
						.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
		} else if (response.getBody() != null && response.getBody().length > 0) {
			nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
					Unpooled.wrappedBuffer(response.getBody()));
		} else {
			nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
		}

		// Apply headers
		for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
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
		cause.printStackTrace();
		ctx.close();
	}
}
